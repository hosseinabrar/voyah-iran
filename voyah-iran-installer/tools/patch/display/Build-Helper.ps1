[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$AndroidSdk,
    [string]$Platform = 'android-36',
    [string]$BuildTools = '37.0.0'
)
$ErrorActionPreference = 'Stop'
$java = Join-Path $JavaHome 'bin/java.exe'
$android = Join-Path $AndroidSdk "platforms/$Platform/android.jar"
$d8 = Join-Path $AndroidSdk "build-tools/$BuildTools/lib/d8.jar"
$apktool = Join-Path (Split-Path $PSScriptRoot) 'apktool.jar'
foreach ($inputFile in @($java,$android,$d8,$apktool)) {
    if (-not (Test-Path -LiteralPath $inputFile -PathType Leaf)) { throw "Missing: $inputFile" }
}
# Developer tool only. Published installers contain precompiled helper smali, not the SDK.
$job = Join-Path ([IO.Path]::GetTempPath()) ('VoyahIranDisplay-' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $job 'classes'
$dex = Join-Path $job 'dex'
$decoded = Join-Path $job 'decoded'
New-Item -ItemType Directory -Path $dex -Force | Out-Null
& $java (Join-Path $PSScriptRoot 'CompileDisplay.java') $PSScriptRoot $android $classes
if ($LASTEXITCODE -ne 0) { throw 'Helper compilation failed' }
# Never include compile-only Android/WM stubs in the DEX.
$inputs = @(Get-ChildItem (Join-Path $classes 'com/android/server/wm') -Filter 'VoyahIranDisplayPolicy*.class' | ForEach-Object FullName)
if ($inputs.Count -ne 4) { throw "Unexpected helper class count: $($inputs.Count)" }
& $java -cp $d8 com.android.tools.r8.D8 --min-api 30 --lib $android --output $dex @inputs
if ($LASTEXITCODE -ne 0) { throw 'D8 failed' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = Join-Path $job 'helper.jar'
[IO.Compression.ZipFile]::CreateFromDirectory($dex,$jar)
& $java -jar $apktool d -r -o $decoded $jar
if ($LASTEXITCODE -ne 0) { throw 'Helper disassembly failed' }
$generated = @(Get-ChildItem (Join-Path $decoded 'smali/com/android/server/wm') -Filter 'VoyahIranDisplayPolicy*.smali')
if ($generated.Count -ne 4) { throw 'Unexpected generated helper count' }
$testClasses = Join-Path $job 'tests'
& $java (Join-Path $PSScriptRoot 'CompileDisplay.java') $PSScriptRoot $android $testClasses test
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& $java -cp "$testClasses;$android" com.android.server.wm.DisplayPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Display policy tests failed' }
foreach ($item in $generated) { Copy-Item -LiteralPath $item.FullName -Destination (Join-Path $PSScriptRoot ('helpers/' + $item.Name)) }
Write-Host "Helper and tests complete. Inspection artifacts: $job"
Write-Host 'Run FrameworkPatch on a local original and on its patched output before publishing.'
