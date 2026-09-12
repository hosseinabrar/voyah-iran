using System.Windows;
using VoyahIran.Installer.Core;

namespace VoyahIran.Installer;

internal static class RebootPrompt
{
    public static bool Confirm(Window owner, bool afterInstall = false) => MessageBox.Show(owner,
        (afterInstall ? "APK نصب شد. اگر برنامه در لانچر دیده نمی‌شود، راه‌اندازی مجدد نمایشگر می‌تواند لیست را به‌روز کند.\n\n" : "") +
        "نمایشگر اکنون راه‌اندازی مجدد شود؟ خودرو متوقف و کابل متصل باشد.",
        "راه‌اندازی مجدد نمایشگر", MessageBoxButton.YesNo, MessageBoxImage.Question, MessageBoxResult.No) == MessageBoxResult.Yes;

    public static async Task RunAsync(Window owner, IAdbClient adb, SessionLog log, string serial)
    {
        try
        {
            await new DeviceRebooter(adb, log).RebootAsync(serial);
            MessageBox.Show(owner, "نمایشگر راه‌اندازی مجدد شد و دوباره متصل است.", "راه‌اندازی مجدد", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            log.Write("Display reboot failed or unverified: " + ex);
            MessageBox.Show(owner, ex.Message, "راه‌اندازی مجدد تأیید نشد", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }
}
