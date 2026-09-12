[CmdletBinding()]
param([string]$Output)
$ErrorActionPreference = 'Stop'
if (-not $Output) { $Output = Join-Path $PSScriptRoot '..\dist' }
dotnet publish (Join-Path $PSScriptRoot '..\src\VoyahIran.Installer\VoyahIran.Installer.csproj') -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:EnableCompressionInSingleFile=true -o $Output
if ($LASTEXITCODE -ne 0) { throw 'Publish failed.' }
Write-Host "Single EXE: $Output\VoyahIran.Installer.exe"
