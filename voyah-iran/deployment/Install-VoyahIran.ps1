[CmdletBinding()]
param(
    [ValidateSet('Install','Status')][string]$Action = 'Install',
    [string]$Apk,
    [string]$Adb = '.\adb.exe',
    [string]$Serial
)
$ErrorActionPreference = 'Stop'
$adbExe = (Get-Command $Adb -ErrorAction Stop).Source
$deviceArgs = @()
function Invoke-Adb([string[]]$Arguments) {
    # Windows PowerShell 5.1 turns native stderr into ErrorRecord objects.
    # adb push reports successful transfers there; only the exit code means failure.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $result = & $adbExe @deviceArgs @Arguments 2>&1
        $nativeExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($nativeExitCode -ne 0) { throw "ADB failed (exit $nativeExitCode): $($result -join "`n")" }
    return ($result -join "`n")
}
$devices = Invoke-Adb @('devices')
if ($Serial) { $deviceArgs = @('-s', $Serial) }
else {
    $online = @($devices -split "`n" | Where-Object { $_ -match '^\S+\s+device\s*$' })
    if ($online.Count -ne 1) { throw 'Connect exactly one authorized device, or specify -Serial.' }
    $deviceArgs = @('-s', (($online[0] -split '\s+')[0]))
}
if ($Action -eq 'Status') {
    Invoke-Adb @('shell','getprop','init.svc.voyahiran_bridge')
    Invoke-Adb @('shell','dumpsys','package','com.abrar.voyahiran') | Select-String 'versionName|versionCode|userId='
    Invoke-Adb @('logcat','-d','-s','VI/BridgeServer:I','VI/Bridge:I','VI/TimeZone:I','*:S')
    exit
}
if (-not $Apk) { throw 'Specify -Apk with the newly built Voyah Iran APK.' }
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$apkHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
# Verify this APK contains the bridge before installing it (old builds do not).
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($apkPath)
try {
    $hasBridge = $false
    foreach ($entry in $archive.Entries | Where-Object { $_.FullName -match '^classes\d*\.dex$' }) {
        $stream = $entry.Open()
        $memory = New-Object IO.MemoryStream
        try {
            $stream.CopyTo($memory)
            if ([Text.Encoding]::ASCII.GetString($memory.ToArray()).Contains('Lcom/abrar/voyahiran/core/bridge/BridgeServer;')) { $hasBridge = $true }
        } finally { $stream.Dispose(); $memory.Dispose() }
    }
    if (-not $hasBridge) { throw 'This APK does not contain the new bridge. Build the updated project first.' }
} finally { $archive.Dispose() }
if ((Invoke-Adb @('shell','su','0','id','-u')).Trim() -ne '0') { throw 'ADB shell su 0 must provide root.' }
$stageDirectory = '/data/local/tmp/voyahiran-stage-' + [Guid]::NewGuid().ToString('N')
$stage = "$stageDirectory/payload"
$tokenPath = [IO.Path]::GetTempFileName()
$shellPath = [IO.Path]::GetTempFileName()
$rcPath = [IO.Path]::GetTempFileName()
try {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $token = ([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant()
    [IO.File]::WriteAllText($tokenPath, $token, [Text.Encoding]::ASCII)
    [IO.File]::WriteAllText($shellPath, ([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'install-bridge.sh'))).Replace("`r`n","`n"), [Text.Encoding]::ASCII)
    [IO.File]::WriteAllText($rcPath, ([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'voyahiran-bridge.rc'))).Replace("`r`n","`n"), [Text.Encoding]::ASCII)
    Write-Host 'Installing APK with its existing signature; application data is preserved.'
    Invoke-Adb @('install','-r','--user','0',$apkPath)
    Invoke-Adb @('shell','su','0','mkdir','-m','700',$stageDirectory) | Out-Null
    Invoke-Adb @('shell','su','0','chown','2000:2000',$stageDirectory) | Out-Null
    Invoke-Adb @('push',$tokenPath,"$stage.token") | Out-Null
    Invoke-Adb @('push',$rcPath,"$stage.rc") | Out-Null
    Invoke-Adb @('push',$shellPath,"$stage.sh") | Out-Null
    Invoke-Adb @('shell','su','0','chmod','600',"$stage.token") | Out-Null
    foreach ($item in @(@('frida-inject','inject'), @('hook_camera_front_roa_switch.js','hook'))) {
        $source = Join-Path $PSScriptRoot ('roa\' + $item[0])
        $digest = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
        $hashFile = [IO.Path]::GetTempFileName()
        try {
            [IO.File]::WriteAllText($hashFile, $digest, [Text.Encoding]::ASCII)
            Invoke-Adb @('push',$source,"$stage.roa-$($item[1])") | Out-Null
            Invoke-Adb @('push',$hashFile,"$stage.roa-$($item[1]).sha256") | Out-Null
        } finally { Remove-Item -LiteralPath $hashFile -Force }
    }
    Invoke-Adb @('shell','su','0','/system/bin/sh',"$stage.sh",$stage,$apkHash)
    Write-Host 'Installation finished. Reboot the vehicle once, then run -Action Status and test the app.'
} finally {
    Remove-Item -LiteralPath $tokenPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $shellPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $rcPath -Force -ErrorAction SilentlyContinue
    try { Invoke-Adb @('shell','su','0','rm','-f',"$stage.token","$stage.rc","$stage.sh","$stage.roa-inject","$stage.roa-hook","$stage.roa-inject.sha256","$stage.roa-hook.sha256") | Out-Null }
    catch { Write-Warning "Temporary file cleanup failed: $_" }
    try { Invoke-Adb @('shell','su','0','rmdir',$stageDirectory) | Out-Null }
    catch { Write-Warning "Temporary directory cleanup failed: $_" }
}

