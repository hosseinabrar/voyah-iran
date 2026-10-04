[CmdletBinding()]
param([string]$SettingsPath)
$ErrorActionPreference='Stop'
$settingsDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $SettingsPath) { $SettingsPath = Join-Path $settingsDirectory 'BuildSettings.local.json' }
. (Join-Path $settingsDirectory 'Build-Settings.ps1')
$current=Get-VoyahBuildSettings $SettingsPath
Add-Type -AssemblyName PresentationFramework
$window=New-Object Windows.Window
$window.Title='تنظیمات بیلد وویا ایران'
$window.Width=480; $window.SizeToContent='Height'; $window.ResizeMode='NoResize'
$window.WindowStartupLocation='CenterScreen'; $window.FlowDirection='RightToLeft'
$panel=New-Object Windows.Controls.StackPanel
$panel.Margin='24'; $window.Content=$panel
foreach($title in @('رمز فایل امضا (Keystore)','رمز کلید (خالی = همان رمز بالا)')) {
    $label=New-Object Windows.Controls.TextBlock; $label.Text=$title
    [void]$panel.Children.Add($label)
    $field=New-Object Windows.Controls.PasswordBox; $field.Margin='0,8,0,16'; $field.FlowDirection='LeftToRight'
    [void]$panel.Children.Add($field)
    if(-not (Get-Variable store -Scope Local -ErrorAction SilentlyContinue)) {$store=$field} else {$key=$field}
}
try {
    $store.Password=Read-VoyahBuildPassword $current.StorePassword
    $key.Password=Read-VoyahBuildPassword $current.KeyPassword
} catch { [void][Windows.MessageBox]::Show('رمز ذخیره‌شده روی این حساب ویندوز قابل خواندن نیست. دوباره وارد کنید.'); $store.Clear(); $key.Clear() }
$protect=New-Object Windows.Controls.CheckBox
$protect.Content='اعمال پروتکشن'; $protect.IsChecked=$current.ProtectionEnabled; $protect.Margin='0,0,0,16'
[void]$panel.Children.Add($protect)
$minify=New-Object Windows.Controls.CheckBox
$minify.Content='مینی‌فای و کوچک‌سازی منابع (R8)'; $minify.IsChecked=$current.MinifyEnabled
$minify.Margin='0,0,0,16'; $minify.IsEnabled=-not $current.ProtectionEnabled
[void]$panel.Children.Add($minify)
$protect.Add_Checked({ $minify.IsChecked=$true; $minify.IsEnabled=$false })
$protect.Add_Unchecked({ $minify.IsEnabled=$true })
$note=New-Object Windows.Controls.TextBlock
$note.Text='برای تست سریع هر دو تیک را بردارید. پروتکشن به R8 نیاز دارد و با روشن‌شدن آن، مینی‌فای هم فعال می‌شود. امضای اصلی حفظ می‌شود.'
$note.TextWrapping='Wrap'; $note.Margin='0,0,0,16'; [void]$panel.Children.Add($note)
$save=New-Object Windows.Controls.Button; $save.Content='ذخیره'; $save.Height=38; $save.IsDefault=$true
$save.Add_Click({
    try {
        Save-VoyahBuildSettings $SettingsPath ([bool]$protect.IsChecked) $store.Password $key.Password ([bool]$minify.IsChecked)
        $window.DialogResult=$true
    } catch { [void][Windows.MessageBox]::Show('ذخیره نشد. رمز را وارد کنید و دسترسی فایل تنظیمات را بررسی کنید.') }
})
[void]$panel.Children.Add($save)
try { if($window.ShowDialog() -ne $true) { throw 'Build settings cancelled.' } }
finally { $store.Clear(); $key.Clear() }
