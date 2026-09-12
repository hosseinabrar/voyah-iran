namespace VoyahIran.Installer.Core;

public sealed class DeviceRebooter(IAdbClient adb, SessionLog log)
{
    public async Task RebootAsync(string serial)
    {
        var before = await adb.RunAsync(serial, ["shell", "cat", "/proc/sys/kernel/random/boot_id"], 15);
        if (before.ExitCode != 0 || !Guid.TryParse(before.Output.Trim(), out var oldId))
            throw new IOException("ارتباط با دستگاه تأیید نشد؛ راه‌اندازی مجدد انجام نشد.");
        log.Write("User requested display reboot; serial=" + serial);
        var result = await adb.RunAsync(serial, ["reboot"], 20);
        if (result.ExitCode != 0) throw new IOException("فرمان ریبوت تأیید نشد. وضعیت نمایشگر و گزارش را بررسی کنید؛ فرمان خودکار تکرار نمی‌شود.");
        var deadline = DateTime.UtcNow.AddMinutes(5);
        while (DateTime.UtcNow < deadline)
        {
            await Task.Delay(2500);
            try
            {
                var identity = await adb.RunAsync(serial, ["shell", "cat", "/proc/sys/kernel/random/boot_id"], 10, true);
                if (identity.ExitCode != 0 || !Guid.TryParse(identity.Output.Trim(), out var newId) || newId == oldId) continue;
                var boot = await adb.RunAsync(serial, ["shell", "getprop", "sys.boot_completed"], 10, true);
                if (boot.ExitCode == 0 && boot.Output.Trim() == "1")
                {
                    log.Write("Display reboot verified: same device, new boot ID, sys.boot_completed=1.");
                    return;
                }
            }
            catch (TimeoutException) { log.Write("Waiting for display to reconnect after reboot."); }
        }
        throw new TimeoutException("فرمان ریبوت ارسال شد، اما اتصال مجدد در پنج دقیقه تأیید نشد. کابل و نمایشگر را بررسی کنید؛ ریبوت تکرار نشد.");
    }
}
