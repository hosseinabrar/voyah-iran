using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using VoyahIran.Installer.Core;
namespace VoyahIran.Installer;
public partial class PermissionWindow : Window
{
    private readonly ApkInstaller installer;
    private readonly ApkInfo info;
    private readonly Dictionary<CheckBox, PermissionChoice> options;
    private readonly Dictionary<CheckBox, bool> confirmed = new();
    private bool busy;
    public PermissionWindow(ApkInstaller installer, ApkInfo info)
    {
        InitializeComponent(); this.installer = installer; this.info = info;
        options = new[] { Microphone, Camera, Location, InstallApps, BackgroundOption }
            .Zip(ApkInstaller.Choices).ToDictionary(p => p.First, p => p.Second);
        Choices.IsEnabled = false;
    }
    private void Busy(bool value)
    {
        busy = value;
        Choices.IsEnabled = RefreshButton.IsEnabled = CloseButton.IsEnabled = !value;
    }
    private async Task<bool> ReadState()
    {
        bool success = true;
        confirmed.Clear();
        foreach (var (box, choice) in options)
        {
            box.IsEnabled = false; box.IsChecked = false;
            if (!ApkInstaller.Available(info, choice)) continue;
            try { confirmed[box] = await installer.ReadAsync(info, choice); box.IsChecked = confirmed[box]; box.IsEnabled = true; }
            catch { success = false; }
        }
        All.IsChecked = confirmed.Count > 0 && confirmed.Values.All(v => v);
        All.IsEnabled = confirmed.Count > 0;
        return success;
    }
    private async Task Reload()
    {
        if (busy) return;
        Busy(true);
        try { Result.Text = await ReadState() ? "تغییر هر گزینه بلافاصله اعمال می‌شود." : "خواندن بعضی دسترسی‌ها ناموفق بود؛ گزارش نصب را بررسی کنید."; }
        finally { Busy(false); }
    }
    private async void OnLoaded(object sender, RoutedEventArgs e) => await Reload();
    private async void Refresh(object sender, RoutedEventArgs e) => await Reload();
    private async void Change(object sender, RoutedEventArgs e)
    {
        if (busy || sender is not CheckBox box || !confirmed.TryGetValue(box, out var previous)) return;
        bool wanted = box.IsChecked == true; box.IsChecked = previous;
        await ChangeOptions([options[box]], wanted);
    }
    private async void ChangeAll(object sender, RoutedEventArgs e)
    {
        if (busy) return;
        bool wanted = All.IsChecked == true;
        All.IsChecked = confirmed.Count > 0 && confirmed.Values.All(v => v);
        await ChangeOptions(confirmed.Keys.Select(b => options[b]).ToArray(), wanted);
    }
    private async Task ChangeOptions(PermissionChoice[] choices, bool wanted)
    {
        Busy(true);
        bool success = false;
        try { await installer.SetAsync(info, choices, wanted); success = true; }
        catch { /* Full command and rollback errors are recorded in the installation log. */ }
        finally
        {
            bool read = await ReadState();
            Result.Text = success && read ? "دسترسی‌ها به‌روز شدند." : "تغییر یا بررسی دسترسی کامل نشد؛ گزارش نصب را بررسی کنید.";
            Busy(false);
        }
    }
    private void Finish(object sender, RoutedEventArgs e) => Close();
    private void OnClosing(object? sender, CancelEventArgs e) { if (busy) e.Cancel = true; }
}

