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
    private readonly string? bundledApp;
    private readonly IAdbClient adb;
    private readonly string serial;
    public ApkWindow(IAdbClient adb, SessionLog log, string serial, string? bundledPath = null, string? bundledApp = null)
    {
        InitializeComponent(); this.log = log; this.adb = adb; this.serial = serial; installer = new(adb, log, serial);
        this.bundledApp = bundledApp;
        if (bundledPath != null) {
            path = bundledPath;
            Title = bundledApp == "myket" ? "نصب مایکت" : "نصب Gboard";
            Heading.Text = Title; FileLabel.Text = "";
            PickButton.Visibility = InstallButton.Visibility = Visibility.Collapsed;
            RoutedEventHandler? start = null;
            start = (_, _) => { Loaded -= start; Install(this, new RoutedEventArgs()); };
            Loaded += start;
        }
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
            var expected = bundledApp == "myket" ? "ir.mservices.market" : bundledApp == "gboard" ? "com.google.android.inputmethod.latin" : null;
            if (expected != null && info.Package != expected) throw new InvalidDataException("هویت فایل نصب مطابق انتظار نیست.");
            await installer.InstallAsync(snapshot, info);
            log.Write("APK installation succeeded: " + info.Package);
            if (bundledApp == "myket") {
                var choice = ApkInstaller.Choices.Single(c => c.Names.Contains("REQUEST_INSTALL_PACKAGES"));
                if (!ApkInstaller.Available(info, choice)) throw new InvalidDataException("مایکت مجوز نصب برنامه را در فایل نصب درخواست نکرده است.");
                await installer.SetAsync(info, new[] { choice }, true);
                log.Write("Myket installed and install-packages permission verified.");
                FileLabel.Text = "مایکت نصب شد و دسترسی نصب برنامه فعال شد.";
            } else if (bundledApp == "gboard") {
                FileLabel.Text = "Gboard نصب شد. از تنظیمات وویا ایران فعالش کنید.";
            } else new PermissionWindow(installer, info) { Owner = this }.ShowDialog();
            if (RebootPrompt.Confirm(this, afterInstall: true))
            {
                Title = "در انتظار راه‌اندازی مجدد نمایشگر…";
                await RebootPrompt.RunAsync(this, adb, log, serial);
                Title = bundledApp == "myket" ? "نصب مایکت" : bundledApp == "gboard" ? "نصب Gboard" : "نصب APK";
            }
        }
        catch (Exception ex) { log.Write(ex.ToString()); MessageBox.Show(this, ex.Message, "نصب APK ناموفق بود", MessageBoxButton.OK, MessageBoxImage.Error); }
        finally { try { File.Delete(snapshot); } catch (Exception ex) { log.Write(ex.Message); } busy = false; PickButton.IsEnabled = InstallButton.IsEnabled = BackButton.IsEnabled = true; }
    }
    private void Copy(object sender, RoutedEventArgs e) { try { Clipboard.SetText(log.ReadText()); } catch (Exception ex) { MessageBox.Show(this, ex.Message); } }
    private void Back(object sender, RoutedEventArgs e) => Close();
    private void OnClosing(object? sender, CancelEventArgs e) { if (busy) e.Cancel = true; }
}
