[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$JavaHome,[Parameter(Mandatory=$true)][string]$AndroidSdk,[string]$Platform='android-36',[string]$BuildTools='37.0.0')
$ErrorActionPreference='Stop'
$java=Join-Path $JavaHome 'bin/java.exe'
$android=Join-Path $AndroidSdk "platforms/$Platform/android.jar"
$d8=Join-Path $AndroidSdk "build-tools/$BuildTools/lib/d8.jar"
$compiler=Join-Path $PSScriptRoot '../display/CompileDisplay.java'
$job=Join-Path ([IO.Path]::GetTempPath()) ('VoyahIranAndroid9-'+[guid]::NewGuid().ToString('N'))
$classes=Join-Path $job 'classes'
$dex=Join-Path $job 'dex'
$decoded=Join-Path $job 'decoded'
New-Item -ItemType Directory -Path $dex -Force | Out-Null
& $java $compiler $PSScriptRoot $android $classes
if($LASTEXITCODE -ne 0){throw 'Helper compilation failed'}
$inputs=@(Get-ChildItem $classes -Recurse -Filter 'VoyahIranLegacy*.class' | ForEach-Object FullName)
if($inputs.Count -ne 4){throw 'Unexpected helper class count'}
& $java -cp $d8 com.android.tools.r8.D8 --min-api 28 --lib $android --output $dex @inputs
if($LASTEXITCODE -ne 0){throw 'D8 failed'}
& $java -cp (Join-Path $PSScriptRoot 'smali-runtime.jar') (Join-Path $PSScriptRoot 'Android9Patch.java') --decode-helper (Join-Path $dex 'classes.dex') $decoded
if($LASTEXITCODE -ne 0){throw 'Helper decode failed'}
$tests=Join-Path $job 'tests'
& $java $compiler $PSScriptRoot $android $tests test
if($LASTEXITCODE -ne 0){throw 'Tests failed to compile'}
& $java -cp "$tests;$android" com.android.server.am.LegacyDisplayTest
if($LASTEXITCODE -ne 0){throw 'Display tests failed'}
& $java -cp "$tests;$android" com.android.server.wm.LegacyFrameTest
if($LASTEXITCODE -ne 0){throw 'Frame tests failed'}
Get-ChildItem $decoded -Recurse -File -Filter '*.smali' | ForEach-Object {
    $destination=Join-Path (Join-Path $PSScriptRoot 'helpers') $_.FullName.Substring($decoded.Length+1)
    New-Item -ItemType Directory -Path (Split-Path $destination) -Force | Out-Null
    Copy-Item -LiteralPath $_.FullName -Destination $destination
}
Write-Host "Helpers built; verify server links and run Android9Patch on original and patched firmware before publishing. Artifacts: $job"
