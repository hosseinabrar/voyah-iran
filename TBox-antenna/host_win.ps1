param([ValidateSet('install','uninstall')][string]$Action='install')
# Windows PowerShell 5 treats native stderr as ErrorRecords; check exit codes explicitly.
$ErrorActionPreference='Continue'
Set-Location $PSScriptRoot
$adb=Join-Path $PSScriptRoot 'tools\windows\adb.exe'
New-Item -ErrorAction Stop -ItemType Directory -Force logs | Out-Null
$log=Join-Path $PSScriptRoot ('logs\install-'+(Get-Date -Format yyyyMMdd-HHmmss)+'.log')
function Invoke-Adb([string[]]$Arguments) {
 $output=& $adb @Arguments 2>&1
 $code=$LASTEXITCODE
 $output | Out-File -Append -Encoding utf8 $log
 if($code -ne 0){throw "ADB command failed ($code). See $log"}
 return $output
}
try {
 Invoke-Adb @('start-server') | Out-Null
 $targets=@()
 foreach($line in (Invoke-Adb @('devices'))) {
  if("$line" -match '^(\S+)\s+device$') {
   $candidate=$Matches[1]
   & $adb -s $candidate shell 'test -f /usr/etc/software_versions.xml' 2>&1 | Out-File -Append $log
   if($LASTEXITCODE -eq 0){$targets+= $candidate}
  }
 }
 if($targets.Count -ne 1){throw 'Connect exactly one authorized T-box'}
 $serial=$targets[0]
 if($Action -eq 'uninstall') {
  Invoke-Adb @('-s',$serial,'shell','/bin/sh','/usr/local/lib/TBox-antenna/uninstall.sh') | Write-Host
  exit 0
 }
 Invoke-Adb @('-s',$serial,'shell','mkdir','-p','/tmp/TBox-antenna-stage') | Out-Null
 Invoke-Adb @('-s',$serial,'push','payload/.','/tmp/TBox-antenna-stage/') | Out-Null
 Invoke-Adb @('-s',$serial,'shell','/bin/sh','/tmp/TBox-antenna-stage/install.sh') | Write-Host
 for($i=0;$i -lt 200;$i++) {
  $status=((& $adb -s $serial shell /bin/sh /usr/local/lib/TBox-antenna/status.sh 2>>$log) -join "`n").Trim()
  if($LASTEXITCODE -ne 0){$status='RUNNING'}
  if($status -eq 'SUCCESS' -or $status -eq 'FAILED') {
   Invoke-Adb @('-s',$serial,'shell','cat','/tmp/voyah-tbox-kit.log') | Write-Host
   if($status -eq 'FAILED'){throw 'Device setup failed'}
   Write-Host 'SUCCESS: Installation and T-box Internet checks completed.'
   exit 0
  }
  Start-Sleep -Seconds 5
 }
 throw 'Verification timed out. Reconnect USB and rerun to inspect installation.'
} catch { Write-Host ('FAILED: '+$_.Exception.Message); exit 1 }
