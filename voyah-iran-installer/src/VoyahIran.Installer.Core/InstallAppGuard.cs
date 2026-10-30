using System.Text.RegularExpressions;

namespace VoyahIran.Installer.Core;

// PackageManager's disabled-user state prevents every explicit activity/service restart,
// including old injected hooks that do not know about a new installer-only flag.
public sealed class InstallAppGuard(Func<string[], Task<string>> command, string recoveryFile)
{
    public const string Package = "com.abrar.voyahiran";
    private int? original;
    private bool changed;
    public bool CanLaunch => original is null or 0 or 1;
    public static int ReadState(string dump)
    {
        var match = Regex.Match(dump, @"(?m)^\s*User 0:.*\benabled=(\d+)\b");
        if (!match.Success || !int.TryParse(match.Groups[1].Value, out var state) || state is < 0 or > 4)
            throw new IOException("Cannot verify package enabled state for user 0; installation cancelled.");
        return state;
    }
    private async Task<int> State() => ReadState(await command(["/system/bin/dumpsys", "package", Package]));
    public async Task Capture()
    {
        var path = await command(["/system/bin/pm", "list", "packages", "--user", "0", Package]);
        if (!path.Split('\n').Any(l => l.Trim() == "package:" + Package)) return;
        original = await State();
        await File.WriteAllTextAsync(recoveryFile, $"Package={Package}\nUser=0\nOriginalEnabledState={original}\nRestoreCommand=pm {RestoreVerb(original.Value)} --user 0 {Package}\n");
    }
    // Call only AFTER the frozen owner's embedded map tasks have been removed.
    public async Task Block()
    {
        if (original == null) return;
        changed = true;
        await command(["/system/bin/pm", "disable-user", "--user", "0", Package]);
        await Verify();
    }
    public async Task Verify()
    {
        if (changed && await State() != 3)
            throw new IOException("App restart prevention is not active; installation cancelled.");
    }
    private static string RestoreVerb(int state) => state switch {
        0 => "default-state", 1 => "enable", 2 => "disable", 3 => "disable-user", 4 => "disable-until-used",
        _ => throw new ArgumentOutOfRangeException(nameof(state))
    };
    public async Task Restore()
    {
        if (!changed) return;
        await command(["/system/bin/pm", RestoreVerb(original!.Value), "--user", "0", Package]);
        if (await State() != original.Value) throw new IOException("Package state restoration not confirmed; see " + recoveryFile);
        changed = false;
    }
}
