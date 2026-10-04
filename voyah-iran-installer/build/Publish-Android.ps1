[CmdletBinding()]
param(
    [string]$JavaHome,
    [string]$PythonExe,
    [string]$Keystore,
    [string]$Alias = 'voyahiran',
    [switch]$PreparePayload,
    [switch]$ReuseUnchanged
)
$ErrorActionPreference = 'Stop'
$android = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../voyah-iran'))
if (-not $Keystore) { $Keystore = Join-Path $android 'Keystore/voyahiran' }
$Keystore = (Resolve-Path -LiteralPath $Keystore).Path
if (-not $JavaHome) { $JavaHome = $env:JAVA_HOME }
if (-not $JavaHome) { $JavaHome = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr' }
if (-not (Test-Path (Join-Path $JavaHome 'bin/javac.exe'))) { throw 'Install a full JDK and set JAVA_HOME.' }

. (Join-Path $PSScriptRoot 'Build-Settings.ps1')
$settingsPath=Join-Path $PSScriptRoot 'BuildSettings.local.json'
$settings=Get-VoyahBuildSettings $settingsPath
if (-not $settings.StorePassword) {
    & (Join-Path $PSScriptRoot 'Edit-BuildSettings.ps1') -SettingsPath $settingsPath
    $settings=Get-VoyahBuildSettings $settingsPath
}
. (Join-Path $PSScriptRoot 'Payload-Reuse.ps1')
$installerRoot=Split-Path -Parent $PSScriptRoot
$receipt=Join-Path $PSScriptRoot 'PayloadReuse.local.json'
$bundle=Join-Path $installerRoot 'payload/bundle.zip'
$fingerprint=Get-VoyahPayloadFingerprint $android $installerRoot "$JavaHome|$PythonExe|$Keystore|$Alias"
if ($PreparePayload -and $ReuseUnchanged -and (Test-VoyahPayloadReuse $receipt $bundle $fingerprint)) {
    Write-Host 'Reusing unchanged Android payload from the preceding build; skipping Gradle, protection and ZIP packaging.'
    return
}
# Invalidate before work: a failed build must never leave a reusable success receipt.
Remove-Item -LiteralPath $receipt -Force -ErrorAction SilentlyContinue
# Fail closed if another Windows account cannot decrypt these credentials.
$storePassword=Read-VoyahBuildPassword $settings.StorePassword
$keyPassword=Read-VoyahBuildPassword $settings.KeyPassword
if (-not $storePassword) { throw 'Open Build Settings.cmd and enter the signing password.' }
if (-not $keyPassword) { $keyPassword=$storePassword }
$names = @('JAVA_HOME','VI_PUBLISH_KEYSTORE','VI_PUBLISH_ALIAS','VI_DPT_STORE_PASSWORD','VI_DPT_KEY_PASSWORD')
$saved = @{}
foreach ($name in $names) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $env:JAVA_HOME = $JavaHome
    $env:VI_PUBLISH_KEYSTORE = $Keystore
    $env:VI_PUBLISH_ALIAS = $Alias
    $env:VI_DPT_STORE_PASSWORD = $storePassword
    $env:VI_DPT_KEY_PASSWORD = $keyPassword
    $storePassword=$null; $keyPassword=$null
    Write-Host 'Building signed Android release...'
    Push-Location $android
    try {
        $minifyValue=([bool]$settings.MinifyEnabled).ToString().ToLowerInvariant()
        Write-Host "Release minify: $minifyValue"
        & (Join-Path $android 'gradlew.bat') --no-daemon "-Pvoyah.minify=$minifyValue" :app:assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'Android release build failed.' }
    } finally { Pop-Location }
    $built = Join-Path $android 'app/build/outputs/apk/release/app-release.apk'
    if (-not (Test-Path -LiteralPath $built)) { throw 'Signed Gradle APK missing.' }
    $release = Join-Path $android 'app/release/app-release.apk'
    [void][IO.Directory]::CreateDirectory((Split-Path $release))
    Copy-Item -LiteralPath $built -Destination $release -Force
    if (-not $settings.ProtectionEnabled) {
        Write-Host 'TEST BUILD: protection disabled; signed with the original release key.'
        if ($PreparePayload) {
            $payloadOptions=@{ Apk=$release; FastCompression=$true }
            if ($settings.MinifyEnabled) { $payloadOptions.Mapping=Join-Path $android 'app/build/outputs/mapping/release/mapping.txt' }
            & (Join-Path $PSScriptRoot 'Prepare-Payload.ps1') @payloadOptions
            Save-VoyahPayloadReuse $receipt $bundle $fingerprint
        }
        return
    }
    $runs = Join-Path $android 'build/raha-protected'
    $before = @(Get-ChildItem -LiteralPath $runs -Directory -ErrorAction SilentlyContinue | ForEach-Object FullName)
    $protection = Join-Path $android 'deployment/protection'
    & (Join-Path $protection 'Protect-Raha.ps1') -Apk $release -JavaHome $JavaHome -PythonExe $PythonExe
    $created = @(Get-ChildItem -LiteralPath $runs -Directory | Where-Object { $_.FullName -notin $before })
    if ($created.Count -ne 1) { throw 'Cannot identify the protection output of this publish.' }
    & (Join-Path $protection 'Sign-Raha.ps1') -RunDirectory $created[0].FullName -Keystore $Keystore -Alias $Alias -JavaHome $JavaHome -UseEnvironmentPasswords
    Write-Host 'Android build, protection and signing completed.'
    if ($PreparePayload) {
        & (Join-Path $PSScriptRoot 'Prepare-Payload.ps1') -ProtectedRun $created[0].FullName
        Save-VoyahPayloadReuse $receipt $bundle $fingerprint
    }
} finally {
    $storePassword=$null; $keyPassword=$null
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
}
