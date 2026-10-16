using System.Text.RegularExpressions;

namespace VoyahIran.Installer.Core;

// Only installer-owned temporary generations. OEM framework originals and user reports are not caches.
public static class InstallationStorage
{
    public static void CleanPayload(string payload, Action<string> log)
    {
        var full=Path.GetFullPath(payload);
        if(Path.GetFileName(Path.GetDirectoryName(full)) != "Cache" || !Guid.TryParseExact(Path.GetFileName(full),"N",out _))return;
        try {
            if(!Directory.Exists(full) || (File.GetAttributes(Path.GetDirectoryName(full)!) & FileAttributes.ReparsePoint)!=0 || HasLinks(full))return;
            using(var lease=new FileStream(Path.Combine(full,".installer-lease"),FileMode.OpenOrCreate,FileAccess.ReadWrite,FileShare.None)) { }
            Directory.Delete(full,true);
            log($"Removed extracted payload: {full}");
        } catch(Exception e) {log($"Payload cleanup deferred: {e.Message}");}
    }
    public static void CleanLocal(string backupRoot, string activePayload, Action<string> log)
    {
        Clean(backupRoot, @"^[0-9]{8}-[0-9]{6}-[0-9a-f]{32}$", null, log);
        var cache = Path.GetDirectoryName(Path.GetFullPath(activePayload))!;
        if (Path.GetFileName(cache) == "Cache") Clean(cache, @"^[0-9a-f]{32}$", activePayload, log);
    }
    private static bool HasLinks(string directory)
    {
        if ((File.GetAttributes(directory) & FileAttributes.ReparsePoint) != 0) return true;
        foreach (var entry in Directory.EnumerateFileSystemEntries(directory))
        {
            var attributes = File.GetAttributes(entry);
            if ((attributes & FileAttributes.ReparsePoint) != 0) return true;
            if ((attributes & FileAttributes.Directory) != 0 && HasLinks(entry)) return true;
        }
        return false;
    }
    private static void Clean(string root, string pattern, string? keep, Action<string> log)
    {
        if (!Directory.Exists(root) || (File.GetAttributes(root) & FileAttributes.ReparsePoint) != 0) return;
        foreach (var directory in Directory.EnumerateDirectories(root))
        {
            if (!Regex.IsMatch(Path.GetFileName(directory), pattern) ||
                Path.GetFullPath(directory) == (keep == null ? null : Path.GetFullPath(keep))) continue;
            try
            {
                if (HasLinks(directory)) { log($"Cleanup skipped linked directory: {directory}"); continue; }
                // New installers hold this lease; never remove another running installer's files.
                var leasePath = Path.Combine(directory, ".installer-lease");
                using (var lease = new FileStream(leasePath, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None)) { }
                Directory.Delete(directory, true);
                log($"Removed obsolete installation files: {directory}");
            }
            catch (Exception e) { log($"Cleanup deferred for {directory}: {e.Message}"); }
        }
    }

    // Enumerate, validate, then delete an explicit path. Do not touch framework backups,
    // the active bridge, downloads, settings recovery journals, or unknown names.
    public const string RemoteCleanup = """
        set -eu
        [ "$(id -u)" = 0 ] || exit 1
        for dir in /data/local/tmp/voyahiran-backup-*; do
          [ -d "$dir" ] && [ ! -L "$dir" ] || continue
          suffix=${dir##*/voyahiran-backup-}
          case "$suffix" in ''|*[!0-9]*) continue ;; esac
          [ ${#suffix} -ge 9 ] && [ ${#suffix} -le 13 ] || continue
          [ -d "$dir/bridge" ] || [ -f "$dir/service.rc" ] || [ -z "$(ls -A "$dir")" ] || continue
          [ -z "$(find "$dir" -type l -print)" ] || { echo "Cleanup skipped symlink: $dir"; continue; }
          rm -rf "$dir"
          echo "Removed obsolete bridge backup: $dir"
        done
        """;
}
