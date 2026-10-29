namespace VoyahIran.Installer.Core;

/// <summary>Transfer integrity only; compatibility is checked by the local structural patcher.</summary>
public static class Android9Firmware
{
    public static bool IsValidVdexHash(string hash) =>
        System.Text.RegularExpressions.Regex.IsMatch(hash, "\\A[0-9a-f]{64}\\z");
}
