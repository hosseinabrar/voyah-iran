using System.IO;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Markup;
using Microsoft.Win32;

namespace VoyahIran.Installer;

internal static class InstallerTheme
{
    private static readonly string SettingsPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "VoyahIran", "Installer", "appearance.txt");
    internal static string Choice { get; private set; } = "system";
    internal static string Caption => Choice switch { "light" => "تم: روشن", "dark" => "تم: تیره", _ => "تم: سیستم" };
    // Kept identical to Android values/colors.xml and values-night/colors.xml.
    private static readonly (string Key, string Light, string Dark)[] Palette = [
        ("BackgroundBrush", "#F1F3F6", "#191D25"), ("PanelBrush", "#FFFFFF", "#23272F"),
        ("ElevatedBrush", "#E8ECF1", "#2D3442"), ("BorderBrush", "#CDD3DC", "#373F4A"),
        ("TextBrush", "#202732", "#FFFFFF"), ("MutedBrush", "#53606E", "#CACACA"),
        ("AccentBrush", "#247ED4", "#4099F3"), ("AccentSurfaceBrush", "#DCECFB", "#263F59"),
        ("ControlBrush", "#E3E8EF", "#373F4A"), ("SwitchBrush", "#AEB7C2", "#808894"),
        ("ErrorBrush", "#B3261E", "#FFB4AB"), ("SuccessBrush", "#277FCF", "#277FCF"),
        ("WarningBrush", "#8A5700", "#F3C969")
    ];
    private static bool IsDark {
        get {
            if (Choice != "system") return Choice == "dark";
            try { return Registry.GetValue(@"HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize", "AppsUseLightTheme", 1) is int value && value == 0; }
            catch { return false; }
        }
    }
    internal static Brush Brush(string key) => (Brush)Application.Current.Resources[key];
    internal static void Initialize()
    {
        var fonts = new FontFamily();
        fonts.FamilyNames[XmlLanguage.GetLanguage("en-US")] = "Voyah UI";
        fonts.FamilyMaps.Add(new FontFamilyMap { Unicode = "0600-08FF, FB50-FDFF, FE70-FEFF", Target = "pack://application:,,,/Assets/Fonts/#IRANSansX" });
        fonts.FamilyMaps.Add(new FontFamilyMap { Target = "pack://application:,,,/Assets/Fonts/#Roboto" });
        Application.Current.Resources["AppFont"] = fonts;
        Application.Current.Resources["LatinFont"] = fonts;
        try { var saved = File.ReadAllText(SettingsPath).Trim(); if (saved is "system" or "light" or "dark") Choice = saved; } catch (IOException) { } catch (UnauthorizedAccessException) { }
        Apply();
        SystemEvents.UserPreferenceChanged += SystemChanged;
        EventManager.RegisterClassHandler(typeof(Window), FrameworkElement.LoadedEvent, new RoutedEventHandler((sender, _) => Chrome((Window)sender)));
    }
    internal static void Stop() => SystemEvents.UserPreferenceChanged -= SystemChanged;
    private static void SystemChanged(object sender, UserPreferenceChangedEventArgs e) => Application.Current.Dispatcher.BeginInvoke(new Action(() => { if (Choice == "system") Apply(); }));
    internal static void Cycle()
    {
        Choice = Choice switch { "system" => "light", "light" => "dark", _ => "system" };
        try { Directory.CreateDirectory(Path.GetDirectoryName(SettingsPath)!); File.WriteAllText(SettingsPath, Choice); } catch (IOException) { } catch (UnauthorizedAccessException) { }
        Apply();
    }
    private static void Apply()
    {
        foreach (var (key, light, dark) in Palette) {
            var color = (Color)ColorConverter.ConvertFromString(IsDark ? dark : light);
            if (Application.Current.Resources[key] is SolidColorBrush brush && !brush.IsFrozen) brush.Color = color;
            else Application.Current.Resources[key] = new SolidColorBrush(color);
        }
        foreach (Window window in Application.Current.Windows) Chrome(window);
    }
    private static void Chrome(Window window)
    {
        var handle = new WindowInteropHelper(window).Handle;
        if (handle == IntPtr.Zero) return;
        var dark = IsDark ? 1 : 0;
        _ = DwmSetWindowAttribute(handle, 20, ref dark, sizeof(int));
    }
    [DllImport("dwmapi.dll")] private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);
}
