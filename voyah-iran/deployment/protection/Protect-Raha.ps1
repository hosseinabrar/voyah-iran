param(
    [string]$PythonExe,
    [string]$JavaHome,
    [string]$Apk,
    [string]$Mapping
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if (-not $JavaHome) {
    $studioRuntime = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'
    $JavaHome = if (Test-Path (Join-Path $studioRuntime 'bin/javac.exe')) { $studioRuntime } else { $env:JAVA_HOME }
}
if (-not $JavaHome) { throw 'Specify -JavaHome pointing to a full JDK (17 or newer).' }
$javaExe = Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $javaExe)) { throw "Java not found: $javaExe" }
$pythonArgs = @()
function Test-PythonRuntime([string]$Executable, [string[]]$Arguments) {
    # Get-Command also finds the Microsoft Store placeholder. Probe execution
    # and version, rather than assuming an executable name means Python exists.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $probe = & $Executable @Arguments -c 'import sys; print(731091 if sys.version_info >= (3, 10) else 0)' 2>$null
        return ($LASTEXITCODE -eq 0 -and ($probe -join '').Trim() -eq '731091')
    } catch { return $false }
    finally { $ErrorActionPreference = $previous }
}
if ($PythonExe) {
    if (-not (Test-PythonRuntime $PythonExe @())) { throw 'The specified Python must be a working Python 3.10+ interpreter.' }
} else {
    $candidates = @()
    foreach ($commandName in @('py', 'python', 'python3')) {
        $command = Get-Command $commandName -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($command -and $command.Source -notmatch '[\\/]Microsoft[\\/]WindowsApps[\\/]') {
            $candidateArgs = if ($commandName -eq 'py') { @('-3') } else { @() }
            $candidates += @{ Executable = $command.Source; Arguments = $candidateArgs }
        }
    }
    $bundledPython = Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
    if (Test-Path -LiteralPath $bundledPython) { $candidates += @{ Executable = $bundledPython; Arguments = @() } }
    foreach ($candidate in $candidates) {
        if (Test-PythonRuntime $candidate.Executable $candidate.Arguments) {
            $PythonExe = $candidate.Executable
            $pythonArgs = $candidate.Arguments
            break
        }
    }
    if (-not $PythonExe) { throw 'Working Python 3.10+ not found. Install Python or specify -PythonExe. No Python packages are required.' }
}
Write-Host "Using Python: $PythonExe"
if (-not $Apk) { $Apk = Join-Path $projectRoot 'app/release/app-release.apk' }
if (-not $Mapping) { $Mapping = Join-Path $projectRoot 'app/build/outputs/mapping/release/mapping.txt' }
& $PythonExe @pythonArgs (Join-Path $PSScriptRoot 'protect_raha.py') --java $javaExe --apk $Apk --mapping $Mapping
if ($LASTEXITCODE -ne 0) { throw 'Application protection failed. Do not use the partial output.' }
