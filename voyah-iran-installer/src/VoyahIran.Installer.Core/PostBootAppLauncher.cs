using System.Diagnostics;

namespace VoyahIran.Installer.Core;

public static class PostBootAppLauncher
{
    private const string Component = "com.abrar.voyahiran/.ui.MainActivity";
    public static async Task LaunchAsync(IAdbClient adb, string serial, Action<string> log,
        Func<Task>? wait = null)
    {
        var clock = Stopwatch.StartNew();
        wait ??= () => Task.Delay(2000);
        log("در انتظار آماده‌شدن برنامه پس از راه‌اندازی مجدد…");
        for (var attempt = 0; attempt < 10 && clock.Elapsed.TotalSeconds < 60; attempt++)
        {
            int Timeout() => Math.Max(1, Math.Min(10, (int)Math.Ceiling(60 - clock.Elapsed.TotalSeconds)));
            var resolved = await adb.RunAsync(serial, ["shell", "cmd", "package", "resolve-activity",
                "--brief", "--user", "0", "-n", Component], Timeout());
            var ready = resolved.ExitCode == 0 && resolved.Output.Split('\n').Any(line =>
                line.Trim() is Component or "com.abrar.voyahiran/com.abrar.voyahiran.ui.MainActivity");
            if (ready && clock.Elapsed.TotalSeconds < 60)
            {
                // Never retry a timeout/transport failure: execution may already have happened.
                var launch = await adb.RunAsync(serial,
                    ["shell", "am", "start", "--user", "0", "-n", Component], Timeout());
                if (launch.ExitCode == 0 &&
                    !launch.Output.Contains("Error", StringComparison.OrdinalIgnoreCase) &&
                    !launch.Output.Contains("Exception", StringComparison.OrdinalIgnoreCase) &&
                    (launch.Output.Contains("Starting: Intent", StringComparison.Ordinal) ||
                     launch.Output.Contains("intent has been delivered", StringComparison.OrdinalIgnoreCase) ||
                     launch.Output.Contains("current task has been brought", StringComparison.OrdinalIgnoreCase))) return;
                if (!launch.Output.Contains("Error type 3", StringComparison.OrdinalIgnoreCase))
                    throw new IOException("نصب تأیید شد، اما بازکردن برنامه ناموفق بود؛ جزئیات در گزارش ثبت شده است.");
                log("صفحهٔ اصلی هنوز آماده نیست؛ بازکردن برنامه دوباره بررسی می‌شود.");
            }
            else if (resolved.ExitCode != 0 || resolved.Output.Contains("Exception", StringComparison.OrdinalIgnoreCase))
                throw new IOException("بررسی آماده‌بودن برنامه ناموفق بود؛ جزئیات در گزارش ثبت شده است.");
            if (attempt < 9 && clock.Elapsed.TotalSeconds < 58) await wait();
        }
        throw new IOException("فایل‌های نصب تأیید شدند، اما صفحهٔ اصلی برنامه در مهلت مقرر آماده نشد؛ گزارش را برای پشتیبانی بفرستید.");
    }
}
