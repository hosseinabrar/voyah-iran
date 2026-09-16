[CmdletBinding()]
param(
    [string]$JavaHome,
    [string]$PythonExe,
    [string]$Keystore,
    [string]$Alias = 'voyahiran'
)
$ErrorActionPreference = 'Stop'
$android = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../voyah-iran'))
if (-not $Keystore) { $Keystore = Join-Path $android 'Keystore/voyahiran' }
$Keystore = (Resolve-Path -LiteralPath $Keystore).Path
if (-not $JavaHome) { $JavaHome = $env:JAVA_HOME }
if (-not $JavaHome) { $JavaHome = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr' }
if (-not (Test-Path (Join-Path $JavaHome 'bin/javac.exe'))) { throw 'Install a full JDK and set JAVA_HOME.' }

# Visual Studio has no interactive console. Use masked desktop password fields.
Add-Type -AssemblyName PresentationFramework
$window = New-Object Windows.Window
$window.Title = 'Voyah Iran - Release signing'
$window.Width = 440; $window.SizeToContent = 'Height'
$window.WindowStartupLocation = 'CenterScreen'
$window.Topmost = $true; $window.ResizeMode = 'NoResize'
$panel = New-Object Windows.Controls.StackPanel
$panel.Margin = '24'; $window.Content = $panel
foreach ($text in @('Keystore password')) {
    $label = New-Object Windows.Controls.TextBlock; $label.Text = $text
    [void]$panel.Children.Add($label)
}
$store = New-Object Windows.Controls.PasswordBox
$store.Margin = '0,8,0,16'; [void]$panel.Children.Add($store)
$label = New-Object Windows.Controls.TextBlock
$label.Text = 'Key password (leave blank if same)'
[void]$panel.Children.Add($label)
$key = New-Object Windows.Controls.PasswordBox
$key.Margin = '0,8,0,16'; [void]$panel.Children.Add($key)
$button = New-Object Windows.Controls.Button
$button.Content = 'Build and publish'; $button.Height = 36
$button.IsDefault = $true
$button.Add_Click({ if ($store.Password.Length -gt 0) { $window.DialogResult = $true } })
[void]$panel.Children.Add($button)
if ($window.ShowDialog() -ne $true) { throw 'Publish cancelled before Android build.' }

$names = @('JAVA_HOME','VI_PUBLISH_KEYSTORE','VI_PUBLISH_ALIAS','VI_DPT_STORE_PASSWORD','VI_DPT_KEY_PASSWORD')
$saved = @{}
foreach ($name in $names) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $env:JAVA_HOME = $JavaHome
    $env:VI_PUBLISH_KEYSTORE = $Keystore
    $env:VI_PUBLISH_ALIAS = $Alias
    $env:VI_DPT_STORE_PASSWORD = $store.Password
    $env:VI_DPT_KEY_PASSWORD = if ($key.Password.Length) { $key.Password } else { $store.Password }
    $store.Clear(); $key.Clear()
    Write-Host 'Building signed Android release...'
    Push-Location $android
    try {
        & (Join-Path $android 'gradlew.bat') --no-daemon :app:assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'Android release build failed.' }
    } finally { Pop-Location }
    $built = Join-Path $android 'app/build/outputs/apk/release/app-release.apk'
    if (-not (Test-Path -LiteralPath $built)) { throw 'Signed Gradle APK missing.' }
    $release = Join-Path $android 'app/release/app-release.apk'
    [void][IO.Directory]::CreateDirectory((Split-Path $release))
    Copy-Item -LiteralPath $built -Destination $release -Force
    $runs = Join-Path $android 'build/raha-protected'
    $before = @(Get-ChildItem -LiteralPath $runs -Directory -ErrorAction SilentlyContinue | ForEach-Object FullName)
    $protection = Join-Path $android 'deployment/protection'
    & (Join-Path $protection 'Protect-Raha.ps1') -Apk $release -JavaHome $JavaHome -PythonExe $PythonExe
    $created = @(Get-ChildItem -LiteralPath $runs -Directory | Where-Object { $_.FullName -notin $before })
    if ($created.Count -ne 1) { throw 'Cannot identify the protection output of this publish.' }
    & (Join-Path $protection 'Sign-Raha.ps1') -RunDirectory $created[0].FullName -Keystore $Keystore -Alias $Alias -JavaHome $JavaHome -UseEnvironmentPasswords
    Write-Host 'Android build, protection and signing completed.'
} finally {
    $store.Clear(); $key.Clear()
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
}
