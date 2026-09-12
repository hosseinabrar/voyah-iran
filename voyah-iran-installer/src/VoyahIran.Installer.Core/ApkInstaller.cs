using System.IO.Compression;
using System.Text;
using System.Text.RegularExpressions;

namespace VoyahIran.Installer.Core;
public sealed record ApkInfo(string Package, HashSet<string> Permissions)
{
    public static ApkInfo Read(string path)
    {
        using var zip = ZipFile.OpenRead(path);
        var entry = zip.GetEntry("AndroidManifest.xml") ?? throw new InvalidDataException("AndroidManifest.xml missing");
        if (entry.Length > 16 * 1024 * 1024) throw new InvalidDataException("Manifest too large");
        using var stream = entry.Open(); using var memory = new MemoryStream(); stream.CopyTo(memory);
        var b = memory.ToArray();
        ushort U16(int p) => BitConverter.ToUInt16(b, p);
        uint U32(int p) => BitConverter.ToUInt32(b, p);
        if (b.Length < 8 || U16(0) != 3 || U32(4) != b.Length) throw new InvalidDataException("Invalid binary Android manifest");
        var strings = new List<string>(); var permissions = new HashSet<string>(StringComparer.Ordinal); string? package = null;
        string S(uint index) => index < strings.Count ? strings[(int)index] : throw new InvalidDataException("Invalid string index");
        for (int p = U16(2); p < b.Length;)
        {
            int size = checked((int)U32(p + 4)), header = U16(p + 2);
            if (size < header || header < 8 || size > b.Length - p) throw new InvalidDataException("Invalid manifest chunk");
            if (U16(p) == 1)
            {
                int count = checked((int)U32(p + 8)), start = checked((int)U32(p + 20)); bool utf8 = (U32(p + 16) & 256) != 0;
                if (count > size / 4) throw new InvalidDataException("Invalid string pool");
                for (int i = 0; i < count; i++)
                {
                    int q = checked(p + start + (int)U32(p + header + i * 4));
                    int Length8() { int n = b[q++]; return (n & 128) != 0 ? ((n & 127) << 8) | b[q++] : n; }
                    int length;
                    if (utf8) { Length8(); length = Length8(); }
                    else { length = U16(q); q += 2; if ((length & 32768) != 0) { length = ((length & 32767) << 16) | U16(q); q += 2; } length = checked(length * 2); }
                    if (q < p || length < 0 || q + length > p + size) throw new InvalidDataException("Invalid manifest string");
                    strings.Add((utf8 ? Encoding.UTF8 : Encoding.Unicode).GetString(b, q, length));
                }
            }
            else if (U16(p) == 0x102)
            {
                int ext = p + header; string tag = S(U32(ext + 4)); int first = ext + U16(ext + 8), stride = U16(ext + 10), count = U16(ext + 12);
                if (stride < 20 || first + count * stride > p + size) throw new InvalidDataException("Invalid attributes");
                for (int i = 0; i < count; i++)
                {
                    int a = first + i * stride; string name = S(U32(a + 4)); uint raw = U32(a + 8);
                    string? value = raw != uint.MaxValue ? S(raw) : b[a + 15] == 3 ? S(U32(a + 16)) : null;
                    if (tag == "manifest" && name == "package" && U32(a) == uint.MaxValue) package = value;
                    if (tag.StartsWith("uses-permission", StringComparison.Ordinal) && name == "name" && value != null && U32(a) != uint.MaxValue && S(U32(a)) == "http://schemas.android.com/apk/res/android") permissions.Add(value);
                }
            }
            p += size;
        }
        if (package == null || !Regex.IsMatch(package, @"\A[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+\z")) throw new InvalidDataException("Invalid package name");
        return new(package, permissions);
    }
}

public sealed record PermissionChoice(string Title, string[] Names, bool AppOp = false);
public sealed class ApkInstaller(IAdbClient adb, SessionLog log, string serial)
{
    public static readonly PermissionChoice[] Choices = [
        new("میکروفون", ["RECORD_AUDIO"]), new("دوربین", ["CAMERA"]),
        new("موقعیت مکانی", ["ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION"]),
        new("نصب برنامه", ["REQUEST_INSTALL_PACKAGES"], true),
        new("اجرای پس‌زمینه", ["RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND", "idle"], true)
    ];
    public static bool Available(ApkInfo info, PermissionChoice choice) =>
        choice.Names.Contains("idle") || choice.Names.Any(n => info.Permissions.Contains("android.permission." + n));
    private static IEnumerable<string> Atoms(ApkInfo info, PermissionChoice choice) =>
        choice.Names.Where(n => choice.Names.Contains("idle") || info.Permissions.Contains("android.permission." + n));    public async Task InstallAsync(string path, ApkInfo info)
    {
        log.Write($"APK install: {info.Package}; SHA256={PayloadBundle.Digest(path)}");
        var result = await adb.RunAsync(serial, ["install", "-r", "--user", "0", path], 180);
        if (result.ExitCode != 0 || !result.Output.Split('\n').Any(l => l.Trim() == "Success")) throw new InvalidOperationException("نصب APK ناموفق بود؛ گزارش را بررسی کنید.");
        var check = await adb.RunAsync(serial, ["shell", "pm", "path", "--user", "0", info.Package]);
        if (check.ExitCode != 0 || !check.Output.Contains("package:")) throw new InvalidOperationException("وجود برنامه پس از نصب تأیید نشد.");
    }
    private async Task<string> Command(params string[] args)
    {
        var result = await adb.RunAsync(serial, new[] { "shell" }.Concat(args).ToArray());
        if (result.ExitCode != 0 || Regex.IsMatch(result.Output, "Exception|Error|denied", RegexOptions.IgnoreCase))
            throw new InvalidOperationException(result.Output);
        return result.Output.Trim();
    }
    private static bool IsOp(string atom) => atom == "REQUEST_INSTALL_PACKAGES" || atom.StartsWith("RUN_", StringComparison.Ordinal);
    private async Task<string> Read(ApkInfo info, string atom)
    {
        if (atom == "idle")
        {
            var value = await Command("dumpsys", "deviceidle", "whitelist", "=" + info.Package);
            if (value is not ("true" or "false")) throw new InvalidOperationException("Unknown battery exemption state");
            return value;
        }
        if (IsOp(atom))
        {
            var output = await Command("cmd", "appops", "get", "--user", "0", info.Package, atom);
            if (output.Contains("No operations")) return "default";
            var match = Regex.Match(output, Regex.Escape(atom) + @":\s*(allow|deny|ignore|default|foreground)\b");
            if (!match.Success) throw new InvalidOperationException("Unknown app-op state: " + output);
            return match.Groups[1].Value;
        }
        var dump = await Command("dumpsys", "package", info.Package);
        var user = Regex.Match(dump, @"(?ms)^\s*User 0:.*?(?=^\s*User \d+:|\z)");
        if (!user.Success) throw new InvalidOperationException("Cannot read user 0 permissions");
        var grant = Regex.Match(user.Value, Regex.Escape("android.permission." + atom) + @": granted=(true|false)\b");
        if (!grant.Success)
            grant = Regex.Match(dump.Split("User 0:")[0], Regex.Escape("android.permission." + atom) + @": granted=(true|false)\b");
        return grant.Success ? grant.Groups[1].Value : "false";
    }
    private static bool Enabled(string atom, string value) => value is "allow" or "true" || (atom.StartsWith("RUN_", StringComparison.Ordinal) && value == "default");
    public async Task<bool> ReadAsync(ApkInfo info, PermissionChoice choice)
    {
        bool enabled = true;
        foreach (var atom in Atoms(info, choice)) enabled &= Enabled(atom, await Read(info, atom));
        return Available(info, choice) && enabled;
    }
    private async Task Write(ApkInfo info, string atom, string value)
    {
        if (atom == "idle") await Command("dumpsys", "deviceidle", "whitelist", (value == "true" ? "+" : "-") + info.Package);
        else if (IsOp(atom)) await Command("cmd", "appops", "set", "--user", "0", info.Package, atom, value);
        else await Command("pm", value == "true" ? "grant" : "revoke", "--user", "0", info.Package, "android.permission." + atom);
        if (await Read(info, atom) != value) throw new InvalidOperationException("Permission verification failed: " + atom);
    }
    public async Task SetAsync(ApkInfo info, IEnumerable<PermissionChoice> choices, bool enabled)
    {
        var atoms = choices.SelectMany(c => Atoms(info, c)).Distinct().ToArray();
        var previous = new Dictionary<string, string>();
        foreach (var atom in atoms) previous[atom] = await Read(info, atom);
        var attempted = new List<string>();
        try
        {
            foreach (var atom in enabled ? atoms : atoms.Reverse().ToArray())
            {
                attempted.Add(atom);
                var value = IsOp(atom) ? (enabled ? "allow" : "ignore") : (enabled ? "true" : "false");
                log.Write($"Permission change: {info.Package} {atom}={value}");
                await Write(info, atom, value);
                log.Write($"Permission verified: {info.Package} {atom}={value}");
            }
        }
        catch (Exception error)
        {
            log.Write($"Permission change failed: {error}");
            foreach (var atom in attempted.AsEnumerable().Reverse())
                try { await Write(info, atom, previous[atom]); }
                catch (Exception restore) { log.Write($"Permission rollback failed: {atom}: {restore}"); }
            throw;
        }
    }
}
