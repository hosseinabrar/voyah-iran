namespace VoyahIran.Installer.Core;

// Only suspend our components. Other accessibility services remain enabled.
public sealed class InstallAccessibility(Func<string[], Task<string>> command)
{
    private readonly HashSet<string> removed = new(StringComparer.Ordinal);
    private bool wasEnabled;
    private const string Key = "enabled_accessibility_services";
    private static string[] Parse(string value) => value.Trim() is "" or "null" ? [] : value.Trim().Split(':', StringSplitOptions.RemoveEmptyEntries);
    private static bool Ours(string value) => value.StartsWith("com.abrar.voyahiran/", StringComparison.Ordinal);
    private async Task<string[]> Read() => Parse(await command(["/system/bin/settings", "--user", "0", "get", "secure", Key]));
    private Task<string> Write(IEnumerable<string> values)
    {
        var value = string.Join(":", values);
        // adb joins remote arguments into a shell command, including empty values.
        var quoted = "'" + value.Replace("'", "'\"'\"'") + "'";
        return command(["/system/bin/settings", "--user", "0", "put", "secure", Key, quoted]);
    }
    public async Task Suspend()
    {
        var current = await Read();
        var own = current.Where(Ours).ToArray();
        if (own.Length == 0) return;
        if (removed.Count == 0)
            wasEnabled = (await command(["/system/bin/settings", "--user", "0", "get", "secure", "accessibility_enabled"])).Trim() == "1";
        removed.UnionWith(own); // Retain recovery state even if a write completes but ADB fails.
        await Write(current.Where(v => !Ours(v)));
        if ((await Read()).Any(Ours)) throw new IOException("Voyah Iran accessibility services could not be suspended; APK replacement cancelled.");
    }
    public async Task Restore()
    {
        if (removed.Count == 0) return;
        var current = await Read();
        await Write(current.Concat(removed).Distinct(StringComparer.Ordinal));
        var actual = (await Read()).ToHashSet(StringComparer.Ordinal);
        if (!removed.IsSubsetOf(actual)) throw new IOException("Could not restore Voyah Iran accessibility services.");
        // Android may clear this toggle when the last enabled service is removed.
        if (wasEnabled)
            await command(["/system/bin/settings", "--user", "0", "put", "secure", "accessibility_enabled", "1"]);
        removed.Clear();
    }
}
