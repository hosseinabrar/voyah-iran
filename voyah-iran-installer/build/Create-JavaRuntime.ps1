[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Jdk, [Parameter(Mandatory=$true)][string]$Output)
$ErrorActionPreference='Stop'
if (Test-Path -LiteralPath $Output) { throw 'Choose a new output directory; existing files are never deleted.' }
& (Join-Path $Jdk 'bin\jlink.exe') --add-modules java.base,java.desktop,java.logging,java.xml,java.compiler,jdk.compiler,jdk.zipfs,jdk.unsupported --strip-debug --no-header-files --no-man-pages --compress=2 --output $Output
if ($LASTEXITCODE -ne 0) { throw 'Java runtime creation failed.' }
Copy-Item -LiteralPath (Join-Path $Jdk 'NOTICE') -Destination (Join-Path $Output 'NOTICE')
Write-Host 'Runtime prepared. Pass this directory as -JavaRuntime to Prepare-Payload.ps1.'
