using System.Text.RegularExpressions;

namespace VoyahIran.Installer.Core;

public static class LauncherInstallPreparation
{
    public static HashSet<int> PreviewDisplays(string dump)
    {
        var ids = new HashSet<int>();
        foreach (var line in dump.Split('\n').Where(l => l.Contains("DisplayInfo{\"VoyahIran.MapPreview\"")))
        {
            var match = Regex.Match(line, @"\bdisplayId\s+(\d+)");
            if (!line.Contains("owner com.abrar.voyahiran") || !match.Success || !int.TryParse(match.Groups[1].Value, out var id) || id <= 1)
                throw new IOException("Cannot verify launcher preview display ownership.");
            ids.Add(id);
        }
        if (dump.Contains("VoyahIran.MapPreview") && ids.Count == 0)
            throw new IOException("Launcher preview exists but its display ID could not be verified.");
        return ids;
    }

    public static HashSet<string> PreviewPackages(string stacks, HashSet<int> displays)
    {
        var packages = new HashSet<string>();
        var selected = false;
        var recognized = false;
        foreach (var line in stacks.Split('\n'))
        {
            var header = Regex.Match(line, @"^\s*Stack id=\d+.*\bdisplayId=(\d+)");
            if (header.Success) { recognized = true; selected = displays.Contains(int.Parse(header.Groups[1].Value)); }
            else if (selected && line.Contains("taskId="))
            {
                var task = Regex.Match(line, @"\btaskId=\d+:\s+([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)(?:/|\s|$)");
                if (!task.Success || task.Groups[1].Value == "com.abrar.voyahiran")
                    throw new IOException("Cannot identify application on launcher preview display.");
                packages.Add(task.Groups[1].Value);
            }
        }
        if (displays.Count > 0 && !recognized)
            throw new IOException("Activity stack output could not be verified; launcher was not stopped.");
        return packages;
    }
}
