param([string]$Adb = 'adb', [string]$Serial = '')
$ErrorActionPreference = 'Stop'
$deviceArgs = @(); if ($Serial) { $deviceArgs = @('-s', $Serial) }
function Invoke-Adb([string[]]$Arguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $result = & $Adb @deviceArgs @Arguments 2>&1; $code = $LASTEXITCODE }
    finally { $ErrorActionPreference = $previous }
    if ($code -ne 0) { throw "ADB failed ($code): $($result -join [Environment]::NewLine)" }
    return ($result -join "`n")
}
$package = 'com.abrar.voyahclustertest'
$identity = Invoke-Adb -Arguments @('shell','su','0','id')
if ($identity -notmatch 'uid=0\(') { throw 'Root shell is unavailable.' }
Write-Host (Invoke-Adb -Arguments @('shell','am','start','-n',"$package/.MainActivity"))
Start-Sleep -Seconds 1
$paths = Invoke-Adb -Arguments @('shell','pm','path',$package)
$apk = (($paths -split "`n" | Where-Object { $_ -match '^package:.*base\.apk\s*$' } | Select-Object -First 1) -replace '^package:', '').Trim()
if (-not $apk -or $apk -notmatch '^/[a-zA-Z0-9_/+=.~\-]+$') { throw "Cannot resolve installed base APK: $paths" }
$script = @"
#!/system/bin/sh
export CLASSPATH='$apk'
# Stop only the dedicated test helper, so an APK update cannot keep old code alive.
for proc in /proc/[0-9]*; do
  if tr '\000' '\n' < "`$proc/cmdline" 2>/dev/null | grep -Fxq 'com.abrar.voyahclustertest.ClusterProbe'; then
    kill -TERM "`${proc##*/}"
    tries=0
    while [ -d "`$proc" ] && [ "`$tries" -lt 30 ]; do
      sleep 0.1
      tries=`$((tries + 1))
    done
    if [ -d "`$proc" ]; then echo 'Old test helper did not stop'; exit 1; fi
  fi
done
rm -f '/data/user/0/com.abrar.voyahclustertest/files/probe.config'
nohup /system/bin/app_process /system/bin com.abrar.voyahclustertest.ClusterProbe </dev/null >/data/local/tmp/voyah-cluster-test.log 2>&1 &
"@
$temp = [IO.Path]::GetTempFileName()
try {
 [IO.File]::WriteAllText($temp, $script.Replace("`r`n","`n")+"`n", [Text.UTF8Encoding]::new($false))
 Write-Host (Invoke-Adb -Arguments @('push',$temp,'/data/local/tmp/voyah-cluster-test-start.sh'))
 Write-Host (Invoke-Adb -Arguments @('shell','su','0','sh','/data/local/tmp/voyah-cluster-test-start.sh'))
 Start-Sleep -Seconds 2
 Write-Host (Invoke-Adb -Arguments @('shell','su','0','restorecon',"/data/user/0/$package/files/probe.config"))
 Write-Host (Invoke-Adb -Arguments @('shell','su','0','cat','/data/local/tmp/voyah-cluster-test.log'))
 $ready = Invoke-Adb -Arguments @('shell','su','0','test','-s',"/data/user/0/$package/files/probe.config")
 Write-Host 'Ready. Use Apply in Voyah Cluster Test. Restart this helper after reboot.'
} finally { [IO.File]::Delete($temp) }
