[CmdletBinding()]
param([string]$Output)
$ErrorActionPreference = 'Stop'
if (-not $Output) { $Output = Join-Path $PSScriptRoot '..\dist' }
dotnet publish (Join-Path $PSScriptRoot '..\src\VoyahIran.Installer\VoyahIran.Installer.csproj') -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:EnableCompressionInSingleFile=true -o $Output
if ($LASTEXITCODE -ne 0) { throw 'Publish failed.' }
[xml]$project = Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\src\VoyahIran.Installer\VoyahIran.Installer.csproj')
$version = ($project.Project.PropertyGroup | Where-Object { $_.Version } | Select-Object -First 1).Version
Write-Host "Single EXE: $(Join-Path $Output ("voyah-iran-installer_" + $version + ".exe"))"
