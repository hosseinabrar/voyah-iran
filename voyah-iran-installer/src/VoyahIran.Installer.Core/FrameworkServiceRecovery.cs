namespace VoyahIran.Installer.Core;

// Retry only settings/pm invocations that never reached the missing service.
// Never replay arbitrary installation writes or an ambiguous timeout.
public sealed class FrameworkServiceRecovery(IAdbClient adb, Action<string> log, Func<Task>? ready = null)
{
    public async Task<CommandResult> Run(string serial, string[] args, int timeout)
    {
        var result = await adb.RunAsync(serial, args, timeout);
        if (args.Length < 2 || args[0] != "shell" || result.ExitCode == 0) return result;
        var service=args[1] switch {
            "settings" or "/system/bin/settings" => "settings",
            "pm" or "/system/bin/pm" => "package",
            _ => null
        };
        if(service==null || result.Output.Trim()!="cmd: Can't find service: "+service) return result;
        log("Android framework service unavailable; waiting for stable framework services before retrying once.");
        if (ready != null) await ready();
        else await new ApkInstallRecovery(adb, log).Ready(serial);
        return await adb.RunAsync(serial, args, timeout);
    }
}
