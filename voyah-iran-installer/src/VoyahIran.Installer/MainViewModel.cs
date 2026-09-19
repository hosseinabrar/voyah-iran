using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Runtime.CompilerServices;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;
using VoyahIran.Installer.Core;

namespace VoyahIran.Installer;
public class Observable : INotifyPropertyChanged
{
    public event PropertyChangedEventHandler? PropertyChanged;
    protected void Notify([CallerMemberName] string? name = null) => PropertyChanged?.Invoke(this, new(name));
}
public sealed class ActionCommand(Action action, Func<bool>? enabled = null) : ICommand
{
    public bool CanExecute(object? parameter) => enabled?.Invoke() ?? true;
    public void Execute(object? parameter) => action();
    public event EventHandler? CanExecuteChanged;
    public void Refresh() => CanExecuteChanged?.Invoke(this, EventArgs.Empty);
}
public sealed class StepViewModel(string title) : Observable
{
    public string Title { get; } = title;
    public string Symbol { get; private set; } = "○";
    public bool IsRunning { get; private set; }
    public Brush Color { get; private set; } = Brushes.SlateGray;
    public void Set(PhaseState? state)
    {
        IsRunning = state == PhaseState.Running;
        Notify(nameof(IsRunning));
        Symbol = state switch { PhaseState.Running => "◌", PhaseState.Succeeded => "✓", PhaseState.Failed => "✕", _ => "○" };
        Color = new SolidColorBrush((System.Windows.Media.Color)ColorConverter.ConvertFromString(state switch { PhaseState.Running => "#F3C969", PhaseState.Succeeded => "#53D3AE", PhaseState.Failed => "#FF8B8B", _ => "#8393A5" }));
        Notify(nameof(Symbol)); Notify(nameof(Color));
    }
}
public sealed class MainViewModel : Observable, IDisposable
{
    private readonly string dataRoot = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "VoyahIran", "Installer");
    private readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromSeconds(2) };
    private readonly SessionLog log;
    private PayloadBundle? bundle;
    private IAdbClient? adb;
    private Device? device;
    private bool scanning;
    private bool starting;
    private bool disposed;
    private bool progressPage;
    public bool Busy { get; private set; }
    public bool CanLeave => !Busy;
    public string ConnectionText { get; private set; } = "در حال آماده‌سازی…";
    public Brush ConnectionBrush { get; private set; } = Brushes.SlateGray;
    public string VersionText { get; private set; } = "در حال بررسی فایل‌های نصب…";
    public string ProgressTitle { get; private set; } = "نصب وویا ایران";
    public string ProgressSubtitle { get; private set; } = "کابل را تا پایان عملیات جدا نکنید.";
    public Visibility HomeVisibility => progressPage ? Visibility.Collapsed : Visibility.Visible;
    public Visibility ProgressVisibility => progressPage ? Visibility.Visible : Visibility.Collapsed;
    public ObservableCollection<StepViewModel> Steps { get; } = new(new[] { "شناسایی خودرو", "روت، آماده‌سازی سیستم و بکاپ", "ساخت و بررسی پچ نمایش و کیبورد (ممکن است چند دقیقه طول بکشد)", "نصب برنامه", "نصب واسط دسترسی", "اصلاح services.jar", "راه‌اندازی مجدد خودرو", "بررسی نهایی نصب" }.Select(t => new StepViewModel(t)));
    public ActionCommand InstallCommand { get; }
    public ActionCommand ApkCommand { get; }
    public ActionCommand UninstallCommand { get; }
    public ActionCommand MyketCommand { get; }
    public ActionCommand GboardCommand { get; }
    public ActionCommand RebootCommand { get; }
    public ActionCommand HelpCommand { get; }
    public ActionCommand CopyCommand { get; }
    public ActionCommand OpenInstallLogsCommand { get; }
    public ActionCommand CaptureLogCommand { get; }
    private LogcatCapture? capture;
    private string? pendingReport;
    private DateTime reportStoppedAt;
    private bool captureChanging;
    public bool ReportOperationBusy => captureChanging;
    public bool HasReportToSave => capture != null || pendingReport != null;
    public string CaptureLogTitle => capture != null ? "پایان گزارش‌گیری" :
        pendingReport != null ? "ذخیره گزارش" : "شروع گزارش‌گیری";
    public ActionCommand HomeCommand { get; }
    public event Action<string>? LogLine;
    public event Action<int>? StepProgressed;
    public MainViewModel()
    {
        log = new SessionLog(Path.Combine(dataRoot, "Logs"));
        log.LineAdded += line => LogLine?.Invoke(line);
        UninstallCommand = new ActionCommand(async () => await OpenUninstallAsync(), () => !Busy && !captureChanging && bundle != null);
        ApkCommand = new ActionCommand(async () => await OpenApkAsync(), () => !Busy && !captureChanging && bundle != null);
        MyketCommand = new ActionCommand(async () => await OpenApkAsync("myket"), () => !Busy && !captureChanging && bundle != null);
        GboardCommand = new ActionCommand(async () => await OpenApkAsync("gboard"), () => !Busy && !captureChanging && bundle != null);
        RebootCommand = new ActionCommand(async () => await RebootAsync(), () => !Busy && !captureChanging && bundle != null);
        InstallCommand = new ActionCommand(async () => await InstallAsync(), () => !Busy && !captureChanging && bundle != null);
        HelpCommand = new ActionCommand(() => new ConnectionHelpWindow { Owner = Application.Current.MainWindow }.ShowDialog());
        CopyCommand = new ActionCommand(() => Guard(() => Clipboard.SetText(log.ReadText())));
        OpenInstallLogsCommand = new ActionCommand(() => Guard(() =>
        {
            Directory.CreateDirectory(log.DirectoryPath);
            Process.Start(new ProcessStartInfo(log.DirectoryPath) { UseShellExecute = true });
        }));
        CaptureLogCommand = new ActionCommand(async () => await ToggleCaptureAsync(),
            () => !captureChanging && (HasReportToSave || (!Busy && !starting && bundle != null)));
        HomeCommand = new ActionCommand(() => { progressPage = false; PagesChanged(); }, () => !Busy);
        timer.Tick += async (_, _) => await ScanAsync();
    }
    private static void Guard(Action action) { try { action(); } catch (Exception e) { MessageBox.Show(e.Message, "وویا ایران"); } }
    public async Task InitializeAsync()
    {
        try
        {
            log.Write($"Voyah Iran Installer {Assembly.GetExecutingAssembly().GetName().Version?.ToString(3)} (dynamic framework patch); OS={Environment.OSVersion}; 64-bit={Environment.Is64BitProcess}");
            bundle = await Task.Run(() => PayloadBundle.Extract(Assembly.GetExecutingAssembly().GetManifestResourceStream("VoyahIran.Payload.zip") ?? throw new InvalidDataException("Payload missing"), Path.Combine(dataRoot, "Cache", Guid.NewGuid().ToString("N"))));
            adb = new AdbClient(bundle.File("adb.exe"), log);
            VersionText = "نسخهٔ " + bundle.Manifest.AppVersion; Notify(nameof(VersionText));
            log.Write($"Embedded payload verified; app={bundle.Manifest.AppVersion}; APK SHA256={bundle.Manifest.Files["app.apk"]}");
            InstallCommand.Refresh(); ApkCommand.Refresh(); UninstallCommand.Refresh(); MyketCommand.Refresh(); GboardCommand.Refresh(); RebootCommand.Refresh(); CaptureLogCommand.Refresh();
            await ScanAsync(); timer.Start();
        }
        catch (Exception e)
        {
            log.Write(e.ToString()); ConnectionText = "آماده‌سازی نصاب ناموفق بود"; Notify(nameof(ConnectionText));
            progressPage = true; ProgressTitle = "آماده‌سازی ناموفق بود"; ProgressSubtitle = "گزارش را برای پشتیبانی ارسال کنید."; PagesChanged();
        }
    }
    private async Task ScanAsync()
    {
        if (scanning || Busy || adb == null || disposed) return;
        scanning = true;
        try
        {
            var result = await adb.RunAsync(null, ["devices", "-l"], 10, true);
            var devices = DeviceParser.Parse(result.Output);
            device = result.ExitCode == 0 ? DeviceParser.SingleReady(devices) : null;
            var text = result.ExitCode == 0 ? DeviceParser.Describe(devices) : "خطا در ارتباط ADB؛ راهنمای اتصال را ببینید";
            if (text != ConnectionText) { log.Write($"Connection: {text}\n{result.Output.Trim()}"); ConnectionText = text; Notify(nameof(ConnectionText)); }
            ConnectionBrush = device != null ? new SolidColorBrush(Color.FromRgb(83, 211, 174)) : Brushes.SlateGray; Notify(nameof(ConnectionBrush));
        }
        catch (Exception e) { device = null; ConnectionText = "ارتباط خودرو در دسترس نیست"; ConnectionBrush = Brushes.SlateGray; Notify(nameof(ConnectionText)); Notify(nameof(ConnectionBrush)); log.Write(e.Message); }
        finally { scanning = false; }
    }
    private async Task InstallAsync()
    {
        if (Busy || starting || bundle == null || adb == null) return;
        starting = true;
        try { await ScanAsync(); } finally { starting = false; }
        if (device == null) { HelpCommand.Execute(null); return; }
        Busy = true; timer.Stop(); progressPage = true;
        foreach (var step in Steps) step.Set(null);
        ProgressTitle = "در حال نصب وویا ایران"; ProgressSubtitle = "کابل را جدا نکنید؛ در پایان نمایشگر خودکار راه‌اندازی مجدد می‌شود.";
        PagesChanged();
        try
        {
            var engine = new InstallEngine(adb, bundle, log, Path.Combine(dataRoot, "Backups"));
            engine.Progress += update => Application.Current.Dispatcher.Invoke(() =>
            {
                Steps[(int)update.Phase].Set(update.State);
                StepProgressed?.Invoke((int)update.Phase);
                if (update.State == PhaseState.Running && update.Phase is InstallPhase.Reboot or InstallPhase.Verify)
                {
                    ConnectionText = update.Phase == InstallPhase.Reboot ? "در انتظار راه‌اندازی مجدد خودرو…" : "خودرو دوباره متصل شد";
                    ConnectionBrush = update.Phase == InstallPhase.Reboot ? Brushes.Goldenrod : new SolidColorBrush(Color.FromRgb(83, 211, 174));
                    Notify(nameof(ConnectionText)); Notify(nameof(ConnectionBrush));
                }
            });
            await engine.RunAsync(device.Serial);
            ProgressTitle = "وویا ایران آماده است"; ProgressSubtitle = "نصب و اجرای واسط تأیید شد. برنامه روی نمایشگر خودرو باز شده است.";
        }
        catch (Exception e) { ProgressTitle = "نصب کامل نشد"; ProgressSubtitle = e.Message + " گزارش را کپی و برای پشتیبانی ارسال کنید."; }
        finally { Busy = false; PagesChanged(); timer.Start(); }
    }
    private async Task OpenApkAsync(string? bundledApp = null)
    {
        if (Busy || starting || adb == null) return;
        starting = true;
        try
        {
            while (scanning) await Task.Delay(50);
            await ScanAsync();
            if (device == null) { HelpCommand.Execute(null); return; }
            Busy = true; timer.Stop(); PagesChanged();
            new ApkWindow(adb, log, device.Serial, bundledApp == null ? null : bundle!.File("apps/" + bundledApp + ".apk"), bundledApp, bundledApp == "gboard" ? new[] { bundle!.File("apps/gboard-config.xhdpi.apk") } : null) { Owner = Application.Current.MainWindow }.ShowDialog();
        }
        catch (Exception e) { log.Write(e.ToString()); MessageBox.Show(e.Message, "نصب APK"); }
        finally { starting = false; Busy = false; PagesChanged(); timer.Start(); }
    }
    private async Task OpenUninstallAsync()
    {
        if (Busy || starting || adb == null || bundle == null) return;
        starting = true;
        try {
            while (scanning) await Task.Delay(50);
            await ScanAsync();
            if (device == null) { HelpCommand.Execute(null); return; }
            Busy = true; timer.Stop(); PagesChanged();
            new UninstallWindow(adb, log, device.Serial, bundle.File("app.apk")) { Owner = Application.Current.MainWindow }.ShowDialog();
        }
        catch (Exception e) { log.Write(e.ToString()); MessageBox.Show(e.Message, "حذف APK"); }
        finally { starting = false; Busy = false; PagesChanged(); timer.Start(); }
    }
    private async Task RebootAsync()
    {
        if (Busy || starting || adb == null) return;
        starting = true;
        try
        {
            while (scanning) await Task.Delay(50);
            await ScanAsync();
            if (device == null) { HelpCommand.Execute(null); return; }
            var serial = device.Serial;
            Busy = true; timer.Stop(); PagesChanged();
            if (!RebootPrompt.Confirm(Application.Current.MainWindow)) return;
            ConnectionText = "در انتظار راه‌اندازی مجدد نمایشگر…";
            ConnectionBrush = Brushes.Goldenrod;
            Notify(nameof(ConnectionText)); Notify(nameof(ConnectionBrush));
            await RebootPrompt.RunAsync(Application.Current.MainWindow, adb, log, serial);
        }
        catch (Exception e) { log.Write(e.ToString()); MessageBox.Show(e.Message, "راه‌اندازی مجدد"); }
        finally { starting = false; Busy = false; PagesChanged(); await ScanAsync(); timer.Start(); }
    }
    private void CaptureChanged()
    {
        Notify(nameof(CaptureLogTitle));
        CaptureLogCommand.Refresh();
        InstallCommand.Refresh(); ApkCommand.Refresh(); UninstallCommand.Refresh();
        MyketCommand.Refresh(); GboardCommand.Refresh(); RebootCommand.Refresh();
    }
    private async Task ToggleCaptureAsync()
    {
        if (captureChanging) return;
        if (HasReportToSave) { await FinishCaptureAsync(); return; }
        if (Busy || starting || bundle == null || adb == null) return;
        captureChanging = true; CaptureChanged();
        try {
            while (scanning) await Task.Delay(50);
            await ScanAsync();
            if (device == null) { HelpCommand.Execute(null); return; }
            var directory = Path.Combine(dataRoot, "Reports");
            Directory.CreateDirectory(directory);
            var path = Path.Combine(directory, "capture-" + Guid.NewGuid().ToString("N") + ".txt");
            capture = await LogcatCapture.StartAsync(bundle.File("adb.exe"), device.Serial, path, adb);
            log.Write("Logcat capture started: " + path);
            _ = ObserveCaptureAsync(capture);
        }
        catch (Exception e) {
            log.Write(e.ToString());
            MessageBox.Show(Application.Current.MainWindow, "شروع گزارش‌گیری ناموفق بود. اتصال خودرو را بررسی کنید.\n" + e.Message, "گزارش‌گیری");
        }
        finally { captureChanging = false; CaptureChanged(); }
    }
    private async Task ObserveCaptureAsync(LogcatCapture session)
    {
        var result = await session.Completion;
        if (disposed || capture != session || result.RequestedStop) return;
        capture = null; pendingReport = session.FilePath; reportStoppedAt = DateTime.Now;
        session.Dispose(); CaptureChanged();
        log.Write($"Logcat interrupted; exit={result.ExitCode}; error={result.Error}; partial={pendingReport}");
        MessageBox.Show(Application.Current.MainWindow,
            "گزارش‌گیری متوقف شد؛ ممکن است ارتباط خودرو قطع شده باشد. گزارش ثبت‌شده تا این لحظه قابل ذخیره است.",
            "گزارش‌گیری", MessageBoxButton.OK, MessageBoxImage.Information);
        // StartAsync may still be unwinding after an immediate device disconnect.
        while (captureChanging && !disposed) await Task.Delay(50);
        if (!disposed) await FinishCaptureAsync();
    }
    public async Task FinishCaptureAsync()
    {
        if (captureChanging) return;
        captureChanging = true; CaptureChanged();
        try {
            if (capture is { } session) {
                reportStoppedAt = DateTime.Now;
                var result = await session.StopAsync();
                pendingReport = session.FilePath; capture = null; session.Dispose();
                log.Write($"Logcat stopped; exit={result.ExitCode}; error={result.Error}; file={pendingReport}");
                if (result.Error != null) MessageBox.Show(Application.Current.MainWindow,
                    "ثبت گزارش با خطا مواجه شد؛ بخش ذخیره‌شده قابل دریافت است.\n" + result.Error, "گزارش‌گیری");
            }
            if (pendingReport == null) return;
            var dialog = new Microsoft.Win32.SaveFileDialog {
                Title = "ذخیره گزارش خودرو", Filter = "Text log (*.txt)|*.txt", DefaultExt = ".txt",
                AddExtension = true, OverwritePrompt = true,
                FileName = $"log-{reportStoppedAt:yyyy-MM-dd_HH-mm-ss}.txt"
            };
            if (dialog.ShowDialog(Application.Current.MainWindow) != true) return;
            var source = pendingReport;
            await Task.Run(() => File.Copy(source, dialog.FileName, overwrite: true));
            log.Write("Vehicle log saved: " + dialog.FileName);
            var temporary = pendingReport; pendingReport = null;
            try { File.Delete(temporary); } catch (Exception e) when (e is IOException or UnauthorizedAccessException) { log.Write(e.Message); }
            MessageBox.Show(Application.Current.MainWindow, "گزارش ذخیره شد.", "گزارش‌گیری");
        }
        catch (Exception e) {
            log.Write(e.ToString());
            MessageBox.Show(Application.Current.MainWindow,
                "ذخیره گزارش ناموفق بود. گزارش موقت حفظ شده؛ دوباره ذخیره را امتحان کنید.\n" + e.Message, "گزارش‌گیری");
        }
        finally { captureChanging = false; CaptureChanged(); }
    }
    private void PagesChanged()
    {
        foreach (var name in new[] { nameof(HomeVisibility), nameof(ProgressVisibility), nameof(ProgressTitle), nameof(ProgressSubtitle), nameof(CanLeave) }) Notify(name);
        InstallCommand.Refresh(); ApkCommand.Refresh(); UninstallCommand.Refresh(); MyketCommand.Refresh(); GboardCommand.Refresh(); RebootCommand.Refresh(); CaptureLogCommand.Refresh(); HomeCommand.Refresh();
    }
    public void Dispose() { disposed = true; timer.Stop(); capture?.Dispose(); /* ADB may still own its executable: retain verified cache for diagnostics. */ }
}

