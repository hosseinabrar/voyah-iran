[CmdletBinding()]
param(
    [string]$Apk,
    [string]$PlatformTools,
    [string]$JavaRuntime,
    [string]$Apktool,
    [string]$AppVersion
)
$ErrorActionPreference = 'Stop'
if (-not $Apk) { $Apk = Join-Path $PSScriptRoot '..\..\voyah-iran\app\release\app-release.apk' }
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
$runtimePath = (Resolve-Path -LiteralPath $JavaRuntime).Path.TrimEnd('\','/')
if (-not (Test-Path -LiteralPath (Join-Path $runtimePath 'bin\java.exe'))) { throw 'Bundled Java runtime is missing.' }
Get-ChildItem -LiteralPath $runtimePath -File -Recurse | ForEach-Object {
    $name = 'java/' + $_.FullName.Substring($runtimePath.Length + 1).Replace('\','/')
    $files[$name] = $_.FullName
}
# Reject older APKs that cannot issue the new keyboard bridge commands.
$apkArchive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path $Apk))
try {
    $compatible = $false
    $hasRoa = $false
    foreach ($entry in $apkArchive.Entries | Where-Object { $_.FullName -match '^classes\d*\.dex$' }) {
        $memory = New-Object IO.MemoryStream
        $stream = $entry.Open()
        try { $stream.CopyTo($memory); if ([Text.Encoding]::ASCII.GetString($memory.ToArray()).Contains('Lcom/abrar/voyahiran/core/bridge/RoaHookManager;')) { $hasRoa = $true }; if ([Text.Encoding]::ASCII.GetString($memory.ToArray()).Contains('voyah_iran_ime_override')) { $compatible = $true } }
        finally { $stream.Dispose(); $memory.Dispose() }
    }
    if (-not $compatible -or -not $hasRoa) { throw 'Build the updated Android release APK first: the selected APK does not contain the keyboard and ROA bridge.' }
} finally { $apkArchive.Dispose() }
$destination = Join-Path $PSScriptRoot '..\payload\bundle.zip'
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
$temporary = "$destination.$([Guid]::NewGuid().ToString('N')).new"
if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
$hashes = [ordered]@{}
$zip = [IO.Compression.ZipFile]::Open($temporary, [IO.Compression.ZipArchiveMode]::Create)
$sha = [Security.Cryptography.SHA256]::Create()
try {
    foreach ($name in $files.Keys) {
        $bytes = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $files[$name]))
        if ($name.EndsWith('.sha256')) { $bytes = [Text.Encoding]::ASCII.GetBytes(([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-','').ToLowerInvariant()) }
        if ($name.EndsWith('.sh') -or $name.EndsWith('.rc')) { $bytes = [Text.Encoding]::ASCII.GetBytes([Text.Encoding]::UTF8.GetString($bytes).Replace("`r`n","`n").TrimStart([char]0xFEFF)) }
        $hashes[$name] = ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-','').ToLowerInvariant()
        $stream = $zip.CreateEntry($name, [IO.Compression.CompressionLevel]::Optimal).Open()
        try { $stream.Write($bytes,0,$bytes.Length) } finally { $stream.Dispose() }
    }
    $manifest = [ordered]@{ AppVersion=$AppVersion; PatchSchema=1; Files=$hashes }
    $bytes = [Text.Encoding]::UTF8.GetBytes(($manifest | ConvertTo-Json -Depth 5))
    $stream = $zip.CreateEntry('manifest.json').Open()
    try { $stream.Write($bytes,0,$bytes.Length) } finally { $stream.Dispose() }
} finally { $zip.Dispose(); $sha.Dispose() }
Move-Item -LiteralPath $temporary -Destination $destination -Force
Write-Host "Payload prepared: $destination"




