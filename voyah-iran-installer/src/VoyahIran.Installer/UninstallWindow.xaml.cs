using System.ComponentModel;
using System.IO;
using System.Windows;
using System.Windows.Media.Imaging;
using VoyahIran.Installer.Core;
namespace VoyahIran.Installer;
public partial class UninstallWindow : Window
{
    private readonly AppRemoval removal;
    private readonly SessionLog log;
    private bool busy;
    private sealed record AppRow(string Package, string Label, BitmapImage? Image);
    public UninstallWindow(IAdbClient adb, SessionLog log, string serial, string apk)
    {
        InitializeComponent(); this.log = log; removal = new(adb, log, serial, apk);
    }
    private void SetBusy(bool value) { busy = value; Apps.IsEnabled = RefreshButton.IsEnabled = !value; RemoveButton.IsEnabled = !value && Apps.Items.Count > 0; }
    private async void OnLoaded(object sender, RoutedEventArgs e) => await LoadApps();
    private async void Refresh(object sender, RoutedEventArgs e) => await LoadApps();
    private async Task LoadApps()
    {
        if (busy) return;
        SetBusy(true); Status.Text = "در حال دریافت برنامه‌ها…";
        try { await Reload(); Status.Text = Apps.Items.Count == 0 ? "برنامه‌ای برای حذف وجود ندارد." : ""; }
        catch (Exception ex) { log.Write(ex.ToString()); Status.Text = ex.Message; Apps.ItemsSource = null; }
        finally { SetBusy(false); }
    }
    private async Task Reload()
    {
        var apps = await removal.ListAsync();
        Apps.ItemsSource = apps.Select(a => {
            BitmapImage? image = null;
            try {
                if (a.Icon.Length > 0) {
                    using var bytes = new MemoryStream(Convert.FromBase64String(a.Icon));
                    image = new BitmapImage(); image.BeginInit(); image.CacheOption = BitmapCacheOption.OnLoad;
                    image.StreamSource = bytes; image.EndInit(); image.Freeze();
                }
            } catch (Exception ex) { log.Write("Icon decode: " + ex.Message); }
            return new AppRow(a.Package, a.Label, image);
        }).ToArray();
        Apps.SelectedIndex = apps.Count > 0 ? 0 : -1;
    }
    private async void Remove(object sender, RoutedEventArgs e)
    {
        if (busy || Apps.SelectedItem is not AppRow app) return;
        if (MessageBox.Show(this, $"برنامهٔ «{app.Label}» و اطلاعات آن حذف شود؟", "تأیید حذف", MessageBoxButton.YesNo, MessageBoxImage.Warning, MessageBoxResult.No) != MessageBoxResult.Yes) return;
        SetBusy(true); Status.Text = "در حال حذف برنامه…";
        try { await removal.RemoveAsync(app.Package); await Reload(); Status.Text = "برنامه حذف شد."; }
        catch (Exception ex) { log.Write(ex.ToString()); Status.Text = ex.Message; }
        finally { SetBusy(false); }
    }
    private void Copy(object sender, RoutedEventArgs e) { try { Clipboard.SetText(log.ReadText()); } catch (Exception ex) { Status.Text = ex.Message; } }
    private void OnClosing(object? sender, CancelEventArgs e) { if (busy) e.Cancel = true; }
}
