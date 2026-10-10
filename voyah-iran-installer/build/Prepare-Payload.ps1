[CmdletBinding()]
param(
    [string]$Apk,
    [string]$Mapping,
    [string]$ProtectedRun,
    [switch]$FastCompression,
    [string]$PlatformTools,
    [string]$JavaRuntime,
    [string]$Apktool,
    [string]$AppVersion
)
$ErrorActionPreference = 'Stop'
$androidRoot = Join-Path $PSScriptRoot '..\..\voyah-iran'
$roaDescriptor = 'Lcom/abrar/voyahiran/core/bridge/RoaHookManager;'
if (-not $Apk) {
    $runs = Join-Path $androidRoot 'build\raha-protected'
    $signed = if ($ProtectedRun) { Get-Item -LiteralPath (Join-Path $ProtectedRun 'signed-report.json') } else {
        Get-ChildItem -LiteralPath $runs -Filter signed-report.json -Recurse -File |
            Sort-Object { $_.Directory.Name } -Descending | Select-Object -First 1
    }
    if (-not $signed) { throw 'No signed protected APK. Run Protect-Raha.ps1 and Sign-Raha.ps1 before publishing.' }
    $run = $signed.Directory.FullName
    $signReport = Get-Content -LiteralPath $signed.FullName -Raw | ConvertFrom-Json
    $protection = Get-Content -LiteralPath (Join-Path $run 'report.json') -Raw | ConvertFrom-Json
    $Apk = Join-Path $run 'raha-protected.apk'
    if ($protection.static_checks -ne 'passed' -or $protection.r8_boundaries_checked -ne $true -or
        $protection.protection_scope -ne 'application-v2' -or $protection.root_dependency_closure_checked -ne $true) {
        throw 'Protected APK checks have not passed.'
    }
    if ((Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash.ToLowerInvariant() -ne $signReport.sha256) {
        throw 'Signed protected APK hash differs from signed-report.json.'
    }
    $release = Join-Path $androidRoot 'app\release\app-release.apk'
    if ((Get-FileHash -LiteralPath $release -Algorithm SHA256).Hash.ToLowerInvariant() -ne $protection.input_sha256) {
        throw 'Android release has changed. Protect and sign the new release before publishing the installer.'
    }
    # R8 renames this bridge class; validate its actual descriptor in the APK.
    $protectedMappingText = [IO.File]::ReadAllText((Join-Path $run 'mapping.txt'))
    $mapped = [regex]::Match($protectedMappingText, '(?m)^com\.abrar\.voyahiran\.core\.bridge\.RoaHookManager -> ([^:]+):\r?$')
    if (-not $mapped.Success) { throw 'ROA bridge mapping missing from protected release.' }
    $roaDescriptor = 'L' + $mapped.Groups[1].Value.Replace('.', '/') + ';'
    Write-Host "Using signed protected APK: $Apk"
    Write-Host "Protected APK SHA256: $($signReport.sha256)"
}
if ($Mapping) {
    $mappingText=[IO.File]::ReadAllText((Resolve-Path -LiteralPath $Mapping))
    $mapped=[regex]::Match($mappingText, '(?m)^com\.abrar\.voyahiran\.core\.bridge\.RoaHookManager -> ([^:]+):\r?$')
    if (-not $mapped.Success) { throw 'ROA bridge mapping missing from release.' }
    $roaDescriptor='L' + $mapped.Groups[1].Value.Replace('.', '/') + ';'
}
if (-not $PlatformTools) { $PlatformTools = Join-Path $PSScriptRoot '..\tools\platform-tools' }
if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) {
    throw "Android APK missing: $Apk. Build the signed Android release APK first (voyah-iran/app/release/app-release.apk)."
}
if (-not $AppVersion) {
    $gradle = Join-Path $PSScriptRoot '..\..\voyah-iran\app\build.gradle.kts'
    $match = [regex]::Match([IO.File]::ReadAllText($gradle), 'versionName\s*=\s*"([^"]+)"')
    if (-not $match.Success) { throw "Cannot read Android versionName: $gradle" }
    $AppVersion = $match.Groups[1].Value
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$deployment = Join-Path $PSScriptRoot '..\..\voyah-iran\deployment'
$patchTools = Join-Path $PSScriptRoot '..\tools\patch'
if (-not $JavaRuntime) { $JavaRuntime = Join-Path $PSScriptRoot '..\tools\runtime\java' }
if (-not $Apktool) { $Apktool = Join-Path $patchTools 'apktool.jar' }
$files = [ordered]@{
    'adb.exe' = (Join-Path $PlatformTools 'adb.exe')
    'AdbWinApi.dll' = (Join-Path $PlatformTools 'AdbWinApi.dll')
    'AdbWinUsbApi.dll' = (Join-Path $PlatformTools 'AdbWinUsbApi.dll')
    'libwinpthread-1.dll' = (Join-Path $PlatformTools 'libwinpthread-1.dll')
    'NOTICE.txt' = (Join-Path $PlatformTools 'NOTICE.txt')
    'app.apk' = $Apk
    'apps/neshan.apk' = (Join-Path $PSScriptRoot '..\tools\apps\neshan.apk')
    'apps/myket.apk' = (Join-Path $PSScriptRoot '..\tools\apps\myket.apk')
    'apps/gboard-config.xhdpi.apk' = (Join-Path $PSScriptRoot '..\tools\apps\gboard-config.xhdpi.apk')
    'apps/gboard.apk' = (Join-Path $PSScriptRoot '..\tools\apps\gboard.apk')
    'install-bridge.sh' = (Join-Path $deployment 'install-bridge.sh')
    'roa/frida-inject' = (Join-Path $deployment 'roa\frida-inject')
    'roa/hook.js' = (Join-Path $deployment 'roa\hook_camera_front_roa_switch.js')
    'roa/frida-inject.sha256' = (Join-Path $deployment 'roa\frida-inject')
    'roa/hook.sha256' = (Join-Path $deployment 'roa\hook_camera_front_roa_switch.js')
    'roa/FRIDA-COPYING.txt' = (Join-Path $deployment 'roa\FRIDA-COPYING.txt')
    'bridge.rc' = (Join-Path $deployment 'voyahiran-bridge.rc')
    'manage-keyboard-framework.sh' = (Join-Path $patchTools 'manage-dynamic-framework.sh')
    'patch/FrameworkPatch.java' = (Join-Path $patchTools 'FrameworkPatch.java')
    'patch/original-method.smali' = (Join-Path $patchTools 'original-method.smali')
    'patch/helper-method.smali' = (Join-Path $patchTools 'helper-method.smali')
    'patch/APKTOOL-LICENSE.txt' = (Join-Path $patchTools 'APKTOOL-LICENSE.txt')
    'patch/THIRD-PARTY.md' = (Join-Path $patchTools 'THIRD-PARTY.md')
    'patch/apktool.jar' = $Apktool
}
Get-ChildItem (Join-Path $patchTools 'display') -File -Filter '*.smali' | ForEach-Object {
    $files['patch/display/' + $_.Name] = $_.FullName
}
Get-ChildItem (Join-Path $patchTools 'display/helpers') -File -Filter '*.smali' | ForEach-Object {
    $files['patch/display/helpers/' + $_.Name] = $_.FullName
}
Get-ChildItem (Join-Path $patchTools 'display/legacy-center') -File -Filter '*.smali' | ForEach-Object {
    $files['patch/display/legacy-center/' + $_.Name] = $_.FullName
}
Get-ChildItem (Join-Path $patchTools 'display/legacy-before-theme') -File -Filter '*.smali' | ForEach-Object {
    $files['patch/display/legacy-before-theme/' + $_.Name] = $_.FullName
}
Get-ChildItem (Join-Path $patchTools 'display/legacy-before-density') -File -Filter '*.smali' | ForEach-Object {
    $files['patch/display/legacy-before-density/' + $_.Name] = $_.FullName
}
Get-ChildItem (Join-Path $patchTools 'display/legacy-global-density') -File -Filter '*.smali' | ForEach-Object {
    $files['patch/display/legacy-global-density/' + $_.Name] = $_.FullName
}
Get-ChildItem (Join-Path $patchTools 'display/legacy-before-user-density') -File -Filter '*.smali' | ForEach-Object {
    $files['patch/display/legacy-before-user-density/' + $_.Name] = $_.FullName
}
Get-ChildItem (Join-Path $patchTools 'display/legacy-welcome') -File -Filter '*.smali' | ForEach-Object {
    $files['patch/display/legacy-welcome/' + $_.Name] = $_.FullName
}
Get-ChildItem (Join-Path $patchTools 'display/legacy-welcome-variant') -File -Filter '*.smali' | ForEach-Object {
    $files['patch/display/legacy-welcome-variant/' + $_.Name] = $_.FullName
}
Get-ChildItem (Join-Path $patchTools 'display/legacy-merge-virtual') -File -Filter '*.smali' | ForEach-Object {
    $files['patch/display/legacy-merge-virtual/' + $_.Name] = $_.FullName
}
Get-ChildItem (Join-Path $patchTools 'display/legacy-merge-production') -File -Filter '*.smali' | ForEach-Object {
    $files['patch/display/legacy-merge-production/' + $_.Name] = $_.FullName
}
$runtimePath = (Resolve-Path -LiteralPath $JavaRuntime).Path.TrimEnd('\','/')
$legacyPatch = (Resolve-Path -LiteralPath (Join-Path $patchTools 'android9')).Path
Get-ChildItem $legacyPatch -File | Where-Object {
    $_.Name -eq 'Android9Patch.java' -or $_.Extension -in @('.smali', '.jar', '.json') -or $_.Name -like '*LICENSE.txt'
} | ForEach-Object { $files['patch/android9/' + $_.Name] = $_.FullName }
Get-ChildItem (Join-Path $legacyPatch 'helpers'), (Join-Path $legacyPatch 'previous-v1'), (Join-Path $legacyPatch 'previous-v2'), (Join-Path $legacyPatch 'previous-v3'), (Join-Path $legacyPatch 'previous-v4'), (Join-Path $legacyPatch 'previous-v5'), (Join-Path $legacyPatch 'previous-v6'), (Join-Path $legacyPatch 'previous-welcome'), (Join-Path $legacyPatch 'previous-welcome-variant'), (Join-Path $legacyPatch 'previous-merge-virtual'), (Join-Path $legacyPatch 'previous-merge-production') -Recurse -File -Filter '*.smali' | ForEach-Object {
    $relative = $_.FullName.Substring($legacyPatch.Length + 1).Replace('\','/')
    $files['patch/android9/' + $relative] = $_.FullName
}
if (-not (Test-Path -LiteralPath (Join-Path $runtimePath 'bin\java.exe'))) { throw 'Bundled Java runtime is missing.' }
Get-ChildItem -LiteralPath $runtimePath -File -Recurse | ForEach-Object {
    $name = 'java/' + $_.FullName.Substring($runtimePath.Length + 1).Replace('\','/')
    $files[$name] = $_.FullName
}
# Reject older APKs that cannot issue the new keyboard bridge commands.
$apkArchive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path $Apk))
try {
    # Inspect the actual APK, not the source versionName: a stale signed release
    # can otherwise pass packaging and fail when the bridge grants permissions.
    $manifestEntry = $apkArchive.GetEntry('AndroidManifest.xml')
    if (-not $manifestEntry) { throw 'Selected APK has no AndroidManifest.xml.' }
    $manifestMemory = New-Object IO.MemoryStream
    $manifestStream = $manifestEntry.Open()
    try {
        $manifestStream.CopyTo($manifestMemory)
        $manifestBytes = $manifestMemory.ToArray()
        # Android binary XML string pools may use UTF-8 or UTF-16.
        $utf8Manifest = [Text.Encoding]::UTF8.GetString($manifestBytes)
        $utf16Manifest = [Text.Encoding]::Unicode.GetString($manifestBytes)
        foreach ($permission in @('android.permission.RECORD_AUDIO', 'android.permission.SYSTEM_ALERT_WINDOW')) {
            if (-not $utf8Manifest.Contains($permission) -and -not $utf16Manifest.Contains($permission)) {
                throw "Selected release APK is missing $permission. Rebuild the signed Android release at $Apk, then publish the installer again."
            }
        }
    } finally { $manifestStream.Dispose(); $manifestMemory.Dispose() }
    $compatible = $false
    $hasRoa = $false
    $hasNeshanDirect = $false
    $hasInventory = $false
    foreach ($entry in $apkArchive.Entries | Where-Object { $_.FullName -match '^classes\d*\.dex$' }) {
        $memory = New-Object IO.MemoryStream
        $stream = $entry.Open()
        try { $stream.CopyTo($memory); if ([Text.Encoding]::ASCII.GetString($memory.ToArray()).Contains('VI_NESHAN_CONFIG_ENGINE_V1')) { $hasNeshanDirect = $true }; if ([Text.Encoding]::ASCII.GetString($memory.ToArray()).Contains('Lcom/abrar/voyahiran/core/bridge/AppInventory;')) { $hasInventory = $true }; if ([Text.Encoding]::ASCII.GetString($memory.ToArray()).Contains($roaDescriptor)) { $hasRoa = $true }; if ([Text.Encoding]::ASCII.GetString($memory.ToArray()).Contains('voyah_iran_ime_override')) { $compatible = $true } }
        finally { $stream.Dispose(); $memory.Dispose() }
    }
    if (-not $hasNeshanDirect) { throw 'Rebuild the updated Android APK: direct Neshan navigation bridge is missing.' }
    if (-not $hasInventory) { throw 'Rebuild, protect and sign the updated Android APK: application inventory helper is missing.' }
    if (-not $compatible -or -not $hasRoa) { throw 'Build the updated Android release APK first: the selected APK does not contain the keyboard and ROA bridge.' }
} finally { $apkArchive.Dispose() }
$destination = Join-Path $PSScriptRoot '..\payload\bundle.zip'
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
$temporary = "$destination.$([Guid]::NewGuid().ToString('N')).new"
if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
$compression = if ($FastCompression) { [IO.Compression.CompressionLevel]::Fastest } else { [IO.Compression.CompressionLevel]::Optimal }
$hashes = [ordered]@{}
$zip = [IO.Compression.ZipFile]::Open($temporary, [IO.Compression.ZipArchiveMode]::Create)
$sha = [Security.Cryptography.SHA256]::Create()
try {
    foreach ($name in $files.Keys) {
        $bytes = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $files[$name]))
        if ($name.EndsWith('.sha256')) { $bytes = [Text.Encoding]::ASCII.GetBytes(([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-','').ToLowerInvariant()) }
        if ($name.EndsWith('.sh') -or $name.EndsWith('.rc')) { $bytes = [Text.Encoding]::ASCII.GetBytes([Text.Encoding]::UTF8.GetString($bytes).Replace("`r`n","`n").TrimStart([char]0xFEFF)) }
        $hashes[$name] = ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-','').ToLowerInvariant()
        $stream = $zip.CreateEntry($name, $compression).Open()
        try { $stream.Write($bytes,0,$bytes.Length) } finally { $stream.Dispose() }
    }
    $manifest = [ordered]@{ AppVersion=$AppVersion; PatchSchema=1; Files=$hashes }
    $bytes = [Text.Encoding]::UTF8.GetBytes(($manifest | ConvertTo-Json -Depth 5))
    $stream = $zip.CreateEntry('manifest.json').Open()
    try { $stream.Write($bytes,0,$bytes.Length) } finally { $stream.Dispose() }
} finally { $zip.Dispose(); $sha.Dispose() }
Move-Item -LiteralPath $temporary -Destination $destination -Force
Write-Host "Payload prepared: $destination"
