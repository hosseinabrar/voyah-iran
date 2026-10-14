using System.Globalization;
using VoyahIran.Installer.Core;
namespace VoyahIran.Installer.Mac;
public static class MacOperations {
    public static async Task SetClock(IAdbClient adb, string serial) {
        async Task<string> Shell(params string[] args) {
            var r = await adb.RunAsync(serial, ["shell", .. args], 15);
            if (r.ExitCode != 0 || System.Text.RegularExpressions.Regex.IsMatch(r.Output, "error|exception|denied|unknown command", System.Text.RegularExpressions.RegexOptions.IgnoreCase)) throw new IOException(r.Output);
            return r.Output.Trim();
        }
        await Shell("settings", "put", "global", "auto_time", "0");
        await Shell("settings", "put", "global", "auto_time_zone", "0");
        await Shell("cmd", "alarm", "set-timezone", "Etc/UTC");
        try { await Shell("cmd", "alarm", "set-time", DateTimeOffset.UtcNow.ToUnixTimeMilliseconds().ToString(CultureInfo.InvariantCulture)); }
        finally { await Shell("cmd", "alarm", "set-timezone", "GMT+03:30"); }
        var zone = await Shell("getprop", "persist.sys.timezone");
        var auto = await Shell("settings", "get", "global", "auto_time");
        var autoZone = await Shell("settings", "get", "global", "auto_time_zone");
        var before = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        var clock = await Shell("date", "+%s");
        var after = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        if (zone != "GMT+03:30" || auto != "0" || autoZone != "0" || !long.TryParse(clock, out var seconds) || seconds < before - 5 || seconds > after + 5)
            throw new IOException("تنظیم تاریخ و ساعت خودرو تأیید نشد.");
    }
}
