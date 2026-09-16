using System.Text.Json;
using System.Text.RegularExpressions;
namespace VoyahIran.Installer.Core;

public sealed record InstalledApplication(string Package, string Label, string Icon);
public sealed class AppRemoval(IAdbClient adb, SessionLog log, string serial, string inventoryApk)
{
    public async Task<IReadOnlyList<InstalledApplication>> ListAsync()
    {
        var remote = "/data/local/tmp/vi-inventory-" + Guid.NewGuid().ToString("N") + ".apk";
        try
        {
            var push = await adb.RunAsync(serial, ["push", inventoryApk, remote], 180);
            if (push.ExitCode != 0) throw new IOException(push.Output);
            // Only controlled paths and a fixed class name are passed to the remote shell.
            var result = await adb.RunAsync(serial, ["shell", "CLASSPATH=" + remote, "app_process", "/system/bin", "com.abrar.voyahiran.core.bridge.AppInventory"], 90, true);
            if (result.ExitCode != 0 || !result.Output.Split('\n').Any(l => l.Trim() == "VI_APPS_DONE"))
            {
                log.Write("Inventory failed: " + result.Output);
                throw new IOException("دریافت فهرست برنامه‌ها ناموفق بود؛ گزارش را بررسی کنید.");
            }
            var apps = result.Output.Split('\n').Where(l => l.StartsWith("VI_APP:", StringComparison.Ordinal))
                .Select(l => JsonSerializer.Deserialize<InstalledApplication>(l[7..].Trim()) ?? throw new IOException("Invalid application record"))
                .Where(a => ValidPackage(a.Package) && a.Package != "com.abrar.voyahiran")
                .OrderBy(a => a.Label).ToArray();
            log.Write($"Application inventory: {apps.Length} removable apps");
            return apps;
        }
        finally
        {
            try { await adb.RunAsync(serial, ["shell", "rm", "-f", remote]); }
            catch (Exception e) { log.Write("Inventory cleanup: " + e.Message); }
        }
    }
    public static bool ValidPackage(string package) => Regex.IsMatch(package, @"\A[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+\z");
    public async Task RemoveAsync(string package)
    {
        if (!ValidPackage(package) || package == "com.abrar.voyahiran") throw new InvalidOperationException("این برنامه قابل حذف نیست.");
        var inventory = await adb.RunAsync(serial, ["shell", "pm", "list", "packages", "-3", "--user", "0"]);
        if (inventory.ExitCode != 0 || !inventory.Output.Split('\n').Any(l => l.Trim() == "package:" + package))
            throw new InvalidOperationException("برنامه در فهرست برنامه‌های قابل حذف نیست.");
        log.Write("Uninstall requested: " + package);
        var result = await adb.RunAsync(serial, ["uninstall", "--user", "0", package], 90);
        if (result.ExitCode != 0 || !result.Output.Split('\n').Any(l => l.Trim() == "Success"))
            throw new IOException("حذف برنامه ناموفق بود.\n" + result.Output);
        var remaining = await adb.RunAsync(serial, ["shell", "pm", "list", "packages", "--user", "0", package]);
        if (remaining.ExitCode != 0 || remaining.Output.Split('\n').Any(l => l.Trim() == "package:" + package))
            throw new IOException("حذف برنامه تأیید نشد؛ فهرست را دوباره بررسی کنید.");
        log.Write("Uninstall verified: " + package);
    }
}
