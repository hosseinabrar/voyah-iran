param(
    [Parameter(Mandatory=$true)][string]$RunDirectory,
    [Parameter(Mandatory=$true)][string]$Keystore,
    [Parameter(Mandatory=$true)][string]$Alias,
    [string]$Sdk,
    [string]$JavaHome,
    [switch]$SeparateKeyPassword,
    [switch]$UseEnvironmentPasswords
)
$ErrorActionPreference = 'Stop'
$runPath = (Resolve-Path -LiteralPath $RunDirectory).Path
$keyPath = (Resolve-Path -LiteralPath $Keystore).Path
$report = Get-Content -LiteralPath (Join-Path $runPath 'report.json') -Raw | ConvertFrom-Json
if ($report.static_checks -ne 'passed') { throw 'Static protection checks have not passed.' }
if ($report.r8_boundaries_checked -ne $true) { throw 'Rebuild and protect again: this old trial did not check R8 inlining boundaries.' }
if ($report.protection_scope -ne 'application-v2' -or $report.root_dependency_closure_checked -ne $true) { throw 'Rebuild protection: application-wide dependency checks are required.' }
$unsigned = Join-Path $runPath 'raha-protected-unsigned.apk'
$inputApk = Join-Path $runPath 'input.apk'
if ((Get-FileHash -LiteralPath $unsigned).Hash.ToLowerInvariant() -ne $report.unsigned_sha256) { throw 'Protected APK hash mismatch.' }
if ((Get-FileHash -LiteralPath $inputApk).Hash.ToLowerInvariant() -ne $report.input_sha256) { throw 'Original APK hash mismatch.' }
if (-not $JavaHome) {
    $studioRuntime = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'
    $JavaHome = if (Test-Path $studioRuntime) { $studioRuntime } else { $env:JAVA_HOME }
}
if (-not $JavaHome) { throw 'Specify -JavaHome.' }
$javaExe = Join-Path $JavaHome 'bin/java.exe'
if (-not $Sdk) {
    $Sdk = $env:ANDROID_HOME
    if (-not $Sdk) { $Sdk = $env:ANDROID_SDK_ROOT }
    if (-not $Sdk) {
        $properties = Join-Path $PSScriptRoot '../../local.properties'
        $sdkLine = Get-Content -LiteralPath $properties | Where-Object { $_ -match '^sdk.dir=' } | Select-Object -First 1
        if ($sdkLine) { $Sdk = $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\') }
    }
}
if (-not $Sdk) { throw 'Specify -Sdk pointing to the Android SDK.' }
$buildTools = Get-ChildItem -LiteralPath (Join-Path $Sdk 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw 'Android SDK Build Tools not found.' }
$signer = Join-Path $buildTools.FullName 'lib/apksigner.jar'
$aligner = Join-Path $buildTools.FullName 'zipalign.exe'
function Invoke-Signer([string[]]$Arguments) {
    # Windows PowerShell treats native stderr as an error record: use exit status.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $result = & $javaExe -jar $signer @Arguments 2>&1; $exitCode = $LASTEXITCODE }
    finally { $ErrorActionPreference = $previous }
    if ($exitCode -ne 0) { throw "apksigner failed: $($result -join [Environment]::NewLine)" }
    return ($result -join [Environment]::NewLine)
}
function Certificate([string]$ApkPath) {
    $details = Invoke-Signer -Arguments @('verify','--print-certs',$ApkPath)
    # SDK versions print either "Signer #1" or "V2 Signer" / "V3.0 Signer" (and other schemes).
    # Require one unique certificate; never silently accept a different co-signer.
    $certificates = @([regex]::Matches($details, '(?m)^\s*(?:Signer #\d+|V[1-4](?:\.\d+)? Signer): certificate SHA-256 digest:\s*([0-9a-fA-F]{64})\s*$') |
        ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() } | Sort-Object -Unique)
    if ($certificates.Count -ne 1) { throw "Expected one verified APK signing certificate. apksigner output:`n$details" }
    return $certificates[0]
}
$originalCertificate = Certificate $inputApk
$final = Join-Path $runPath 'raha-protected.apk'
if (Test-Path -LiteralPath $final) { throw 'Signed output already exists. Keep it or start a new protection run.' }
$candidate = Join-Path $runPath 'raha-protected-candidate.apk'
$aligned = Join-Path $runPath 'raha-protected-aligned.apk'
& $aligner -f -P 16 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed.' }
if ($UseEnvironmentPasswords -and (-not $env:VI_DPT_STORE_PASSWORD -or -not $env:VI_DPT_KEY_PASSWORD)) {
    throw 'Signing password environment is missing.'
}
$storeSecret = if ($UseEnvironmentPasswords) {
    ConvertTo-SecureString $env:VI_DPT_STORE_PASSWORD -AsPlainText -Force
} else { Read-Host 'Existing app keystore password' -AsSecureString }
$keySecret = if ($UseEnvironmentPasswords) { ConvertTo-SecureString $env:VI_DPT_KEY_PASSWORD -AsPlainText -Force } elseif ($SeparateKeyPassword) { Read-Host 'Key password' -AsSecureString } else { $storeSecret }
$savedStore = $env:VI_DPT_STORE_PASSWORD
$savedKey = $env:VI_DPT_KEY_PASSWORD
try {
    $env:VI_DPT_STORE_PASSWORD = [Net.NetworkCredential]::new('', $storeSecret).Password
    $env:VI_DPT_KEY_PASSWORD = [Net.NetworkCredential]::new('', $keySecret).Password
    [void](Invoke-Signer -Arguments @('sign','--ks',$keyPath,'--ks-key-alias',$Alias,
        '--ks-pass','env:VI_DPT_STORE_PASSWORD','--key-pass','env:VI_DPT_KEY_PASSWORD',
        '--v1-signing-enabled','true','--v2-signing-enabled','true',
        '--v4-signing-enabled','false','--out',$candidate,$aligned))
} finally {
    $env:VI_DPT_STORE_PASSWORD = $savedStore
    $env:VI_DPT_KEY_PASSWORD = $savedKey
    $storeSecret.Dispose()
    if ($SeparateKeyPassword -or $UseEnvironmentPasswords) { $keySecret.Dispose() }
}
$newCertificate = Certificate $candidate
if ($newCertificate -ne $originalCertificate) { throw 'Wrong signing key: certificate differs from the original APK. Candidate must NOT be installed.' }
& $aligner -c -P 16 4 $candidate
if ($LASTEXITCODE -ne 0) { throw 'Signed APK alignment verification failed.' }
Move-Item -LiteralPath $candidate -Destination $final
@{ apk = $final; sha256 = (Get-FileHash -LiteralPath $final).Hash.ToLowerInvariant(); certificate = $newCertificate; runtime_tested = $false } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runPath 'signed-report.json') -Encoding UTF8
Write-Host "Signed and verified: $final"
Write-Host 'Hardware testing is required. Installer bundle and original release APK were not modified.'
