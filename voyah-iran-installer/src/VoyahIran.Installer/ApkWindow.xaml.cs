using System.ComponentModel;
using System.IO;
using System.Windows;
using Microsoft.Win32;
using VoyahIran.Installer.Core;
namespace VoyahIran.Installer;
public partial class ApkWindow : Window
{
    private readonly ApkInstaller installer;
    private readonly SessionLog log;
    private string? path;
    private bool busy;
    private readonly IAdbClient adb;
    private readonly string serial;
    public ApkWindow(IAdbClient adb, SessionLog log, string serial)
    {
        InitializeComponent(); this.log = log; this.adb = adb; this.serial = serial; installer = new(adb, log, serial);
        log.LineAdded += Append;
        Closed += (_, _) => log.LineAdded -= Append;
    }
    private void Append(string line) => Dispatcher.BeginInvoke(new Action(() => { LogText.AppendText(line); LogText.ScrollToEnd(); }));
    private void Pick(object sender, RoutedEventArgs e)
    {
        var dialog = new OpenFileDialog { Filter = "Android APK (*.apk)|*.apk", CheckFileExists = true, Multiselect = false };
        if (dialog.ShowDialog(this) == true) { path = dialog.FileName; FileLabel.Text = path; InstallButton.IsEnabled = true; }
    }
    private async void Install(object sender, RoutedEventArgs e)
    {
        if (busy || path == null) return;
        busy = true; PickButton.IsEnabled = InstallButton.IsEnabled = BackButton.IsEnabled = false;
        string snapshot = Path.Combine(Path.GetTempPath(), "voyahiran-" + Guid.NewGuid().ToString("N") + ".apk");
        try
        {
            await Task.Run(() => File.Copy(path, snapshot));
            var info = await Task.Run(() => ApkInfo.Read(snapshot));
            await installer.InstallAsync(snapshot, info);
            log.Write("APK installation succeeded: " + info.Package);
            new PermissionWindow(installer, info) { Owner = this }.ShowDialog();
            if (RebootPrompt.Confirm(this, afterInstall: true))
            {
                Title = "در انتظار راه‌اندازی مجدد نمایشگر…";
                await RebootPrompt.RunAsync(this, adb, log, serial);
                Title = "نصب APK";
            }
        }
        catch (Exception ex) { log.Write(ex.ToString()); MessageBox.Show(this, ex.Message, "نصب APK ناموفق بود", MessageBoxButton.OK, MessageBoxImage.Error); }
        finally { try { File.Delete(snapshot); } catch (Exception ex) { log.Write(ex.Message); } busy = false; PickButton.IsEnabled = InstallButton.IsEnabled = BackButton.IsEnabled = true; }
    }
    private void Copy(object sender, RoutedEventArgs e) { try { Clipboard.SetText(log.ReadText()); } catch (Exception ex) { MessageBox.Show(this, ex.Message); } }
    private void Back(object sender, RoutedEventArgs e) => Close();
    private void OnClosing(object? sender, CancelEventArgs e) { if (busy) e.Cancel = true; }
}
