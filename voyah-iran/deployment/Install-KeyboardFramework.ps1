[CmdletBinding()]
param(
    [ValidateSet('Install','Restore','Status')][string]$Action = 'Install',
    [string]$Adb = '.\adb.exe',
    [string]$Serial
)
$ErrorActionPreference = 'Stop'
$adbExe = (Get-Command $Adb -ErrorAction Stop).Source
$deviceArgs = @()
function Invoke-Adb([string[]]$Arguments) {
    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $result = & $adbExe @deviceArgs @Arguments 2>&1
        $nativeExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previous }
    if ($nativeExit -ne 0) { throw "ADB failed ($nativeExit): $($result -join "`n")" }
    return ($result -join "`n")
}
if ($Serial) { $deviceArgs = @('-s', $Serial) }
else {
    $online = @((Invoke-Adb @('devices')) -split "`n" | Where-Object { $_ -match '^\S+\s+device\s*$' })
    if ($online.Count -ne 1) { throw 'Connect exactly one authorized device, or specify -Serial.' }
    $deviceArgs = @('-s', (($online[0] -split '\s+')[0]))
}
if ((Invoke-Adb @('shell','su','0','id','-u')).Trim() -ne '0') { throw 'ADB su 0 root is required.' }
$original = '0faed7ebf1094c62a4f92b4bc533bbb3c08480a2cd847567cb1cff91b2d1d045'
$patched = 'ede647f69dfdfc4ad86af3a9da6b056a8bff47e6fe9e41d33ab3837b2d26f0ce'
$current = ((Invoke-Adb @('shell','su','0','sha256sum','/system/framework/services.jar')) -split '\s+')[0].ToLowerInvariant()
if ($Action -eq 'Status') {
    Write-Host "services.jar SHA256: $current"
    Write-Host "Matches original: $($current -eq $original); matches patch: $($current -eq $patched)"
    Invoke-Adb @('shell','dumpsys','input_method') | Select-String 'mCurMethodId='
    Invoke-Adb @('shell','dumpsys','input_method_ext') | Select-String 'mCurMethodId='
    exit
}
if ($current -notin @($original,$patched)) { throw 'Firmware does not match the analyzed services.jar. No changes made.' }
$payload = Join-Path $PSScriptRoot 'framework\services.jar'
if ($Action -eq 'Install') {
    if ((Get-FileHash -LiteralPath $payload -Algorithm SHA256).Hash.ToLowerInvariant() -ne $patched) { throw 'Local patch hash mismatch.' }
    if ($current -eq $original) {
        $backupDir = Join-Path $PSScriptRoot ('framework\backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
        $backup = Join-Path $backupDir 'services.jar'
        Invoke-Adb @('pull','/system/framework/services.jar',$backup) | Write-Host
        if ((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash.ToLowerInvariant() -ne $original) { throw 'Laptop backup verification failed.' }
        Write-Host "Verified original backup on laptop: $backup"
    }
}
$stage = '/data/local/tmp/voyahiran-keyboard-' + [Guid]::NewGuid().ToString('N')
$tempScript = [IO.Path]::GetTempFileName()
try {
    $script = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'framework\manage-keyboard-framework.sh')).Replace("`r`n","`n")
    [IO.File]::WriteAllText($tempScript,$script,[Text.Encoding]::ASCII)
    Invoke-Adb @('shell','su','0','mkdir','-m','700',$stage) | Out-Null
    Invoke-Adb @('shell','su','0','chown','2000:2000',$stage) | Out-Null
    Invoke-Adb @('push',$tempScript,"$stage/manage.sh") | Write-Host
    if ($Action -eq 'Install') {
        Invoke-Adb @('push',$payload,"$stage/services.jar") | Write-Host
        $remoteOutput = Invoke-Adb @('shell','su','0','sha256sum',"$stage/services.jar")
        $remoteDigest = ($remoteOutput.Trim() -split '\s+')[0].ToLowerInvariant()
        if ($remoteDigest -cne $patched) {
            throw "Transferred JAR hash mismatch. Expected=$patched Actual=[$remoteOutput]. System file was not changed."
        }
        Write-Host "Verified transferred JAR SHA256: $remoteDigest"
    }
    Invoke-Adb @('shell','su','0','sh',"$stage/manage.sh",$Action.ToLowerInvariant(),"$stage/services.jar") | Write-Host
    Write-Host 'No reboot was performed. Reboot the head unit after successful installation/restoration.'
} finally {
    Remove-Item -LiteralPath $tempScript -Force -ErrorAction SilentlyContinue
    try {
        Invoke-Adb @('shell','su','0','rm','-f',"$stage/manage.sh","$stage/services.jar") | Out-Null
        Invoke-Adb @('shell','su','0','rmdir',$stage) | Out-Null
    } catch { Write-Warning "Could not clean staging directory $stage" }
}
