param(
    [Parameter(Mandatory=$true)][string]$Adb,
    [Parameter(Mandatory=$true)][string]$Serial,
    [ValidateSet('status','apply','restore','watch')][string]$Operation='status'
)
$ErrorActionPreference='Stop'
$remote='/data/local/tmp/voyahiran-local-profile-test.sh'
& $Adb -s $Serial push (Join-Path $PSScriptRoot 'local-profile-test.sh') $remote
if($LASTEXITCODE -ne 0) { throw 'Upload failed' }
$identity = (& $Adb -s $Serial shell id -u | Out-String).Trim()
if($LASTEXITCODE -ne 0) { throw 'Cannot determine shell identity' }
if($identity -eq '0') { & $Adb -s $Serial shell sh $remote $Operation }
else { & $Adb -s $Serial shell su 0 sh $remote $Operation }
if($LASTEXITCODE -ne 0) { throw 'Local profile test failed; inspect the message above' }
