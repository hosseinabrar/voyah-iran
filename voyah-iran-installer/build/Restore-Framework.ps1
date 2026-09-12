[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Record, [Parameter(Mandatory=$true)][string]$Adb, [string]$Serial)
$ErrorActionPreference = 'Stop'
$patch = Get-Content -LiteralPath $Record -Raw | ConvertFrom-Json
if ($patch.AlreadyPatched) { throw 'This run did not replace the framework. Use the record from the run that created the patch, or the legacy restore script for legacy installs.' }
foreach ($digest in @($patch.OriginalHash,$patch.PatchedHash)) { if ($digest -cnotmatch '^[0-9a-f]{64}$') { throw 'Invalid patch record.' } }
$adbExe = (Get-Command $Adb -ErrorAction Stop).Source
$deviceArgs = @()
function Invoke-Adb([string[]]$Arguments) {
    $previous = $ErrorActionPreference
    try { $ErrorActionPreference='Continue'; $output=& $adbExe @deviceArgs @Arguments 2>&1; $code=$LASTEXITCODE }
    finally { $ErrorActionPreference=$previous }
    if ($code -ne 0) { throw "ADB failed ($code): $($output -join "`n")" }
    return ($output -join "`n")
}
if ($Serial) { $deviceArgs=@('-s',$Serial) }
else {
    $online=@((Invoke-Adb @('devices')) -split "`n" | Where-Object {$_ -match '^\S+\s+device\s*$'})
    if ($online.Count -ne 1) { throw 'Connect one authorized vehicle or provide -Serial.' }
    $deviceArgs=@('-s',(($online[0] -split '\s+')[0]))
}
$script='/data/local/tmp/voyahiran-framework-backups/' + $patch.OriginalHash + '/restore.sh'
Invoke-Adb @('shell','su','0','/system/bin/sh',$script,'restore','/dev/null',$patch.OriginalHash,$patch.PatchedHash) | Write-Host
Write-Host 'Restore completed. Reboot manually only after success.'
