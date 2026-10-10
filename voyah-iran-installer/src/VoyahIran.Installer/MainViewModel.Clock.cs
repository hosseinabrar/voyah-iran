using System.Globalization;
using System.IO;
using System.Windows;

namespace VoyahIran.Installer;

public sealed partial class MainViewModel
{
    private async Task SetClockAsync()
    {
        if (Busy || starting || captureChanging || CanBusy || adb == null) return;
        starting = true;
        try {
            while (scanning) await Task.Delay(50);
            await ScanAsync();
            if (device == null) { HelpCommand.Execute(null); return; }
            var serial = device.Serial;
            Busy = true; timer.Stop(); PagesChanged();

            async Task<string> Shell(params string[] args)
            {
                var result = await adb.RunAsync(serial, new[] { "shell" }.Concat(args).ToArray(), 15);
                if (result.ExitCode != 0 || result.Output.Contains("error", StringComparison.OrdinalIgnoreCase)
                    || result.Output.Contains("exception", StringComparison.OrdinalIgnoreCase)
                    || result.Output.Contains("denied", StringComparison.OrdinalIgnoreCase)
                    || result.Output.Contains("unknown command", StringComparison.OrdinalIgnoreCase))
                    throw new IOException("تنظیم تاریخ و ساعت ناموفق بود.\n" + result.Output);
                return result.Output.Trim();
            }

            await Shell("settings", "put", "global", "auto_time", "0");
            await Shell("settings", "put", "global", "auto_time_zone", "0");
            await Shell("cmd", "alarm", "set-timezone", "Etc/UTC");
            try {
                // Sample immediately before sending, not when the button was clicked.
                await Shell("cmd", "alarm", "set-time", DateTimeOffset.UtcNow.ToUnixTimeMilliseconds().ToString(CultureInfo.InvariantCulture));
            }
            finally {
                // Same fixed offset as Voyah Iran; no legacy daylight-saving rules.
                await Shell("cmd", "alarm", "set-timezone", "GMT+03:30");
            }

            var zone = await Shell("getprop", "persist.sys.timezone");
            var autoTime = await Shell("settings", "get", "global", "auto_time");
            var autoZone = await Shell("settings", "get", "global", "auto_time_zone");
            var before = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
            var clock = await Shell("date", "+%s");
            var after = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
            if (zone != "GMT+03:30" || autoTime != "0" || autoZone != "0"
                || !long.TryParse(clock, NumberStyles.Integer, CultureInfo.InvariantCulture, out var seconds)
                || seconds < before - 5 || seconds > after + 5)
                throw new IOException("تنظیم تاریخ و ساعت تأیید نشد. گزارش عملیات را بررسی کنید.");
            log.Write("Vehicle clock verified against laptop UTC; timezone GMT+03:30; automatic time and timezone disabled.");
            MessageBox.Show(Application.Current.MainWindow, "تاریخ و ساعت خودرو با لپ‌تاپ هماهنگ شد و منطقهٔ زمانی روی +۳:۳۰ تنظیم شد.", "تنظیم تاریخ و ساعت");
        }
        catch (Exception e) { log.Write(e.ToString()); MessageBox.Show(Application.Current.MainWindow, e.Message, "تنظیم تاریخ و ساعت"); }
        finally { starting = false; Busy = false; PagesChanged(); timer.Start(); }
    }
}
