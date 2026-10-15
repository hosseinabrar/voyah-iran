using System.IO.Compression;
using System.Security.Cryptography;
using System.Text.Json;

namespace VoyahIran.Installer.Core;

public record BundleManifest(string AppVersion, int PatchSchema, Dictionary<string, string> Files, string TargetRuntime = "win-x64");
public sealed class PayloadBundle
{
    public string Root { get; }
    public BundleManifest Manifest { get; }
    private PayloadBundle(string root, BundleManifest manifest) { Root = root; Manifest = manifest; }
    public string File(string name) => Path.Combine(Root, name);
    public string JavaExecutable => File(Manifest.TargetRuntime.StartsWith("osx-", StringComparison.Ordinal) ? "java/bin/java" : "java/bin/java.exe");
    public static string Digest(string path)
    {
        using var stream = System.IO.File.OpenRead(path);
        return Convert.ToHexString(SHA256.HashData(stream)).ToLowerInvariant();
    }
    public static string[] RequiredFiles(string targetRuntime = "win-x64")
    {
        string[] required = ["adb.exe", "AdbWinApi.dll", "AdbWinUsbApi.dll", "libwinpthread-1.dll", "NOTICE.txt", "app.apk", "apps/neshan.apk", "apps/myket.apk", "apps/gboard.apk", "apps/gboard-config.xhdpi.apk", "install-bridge.sh", "bridge.rc", "manage-keyboard-framework.sh",
            "roa/frida-inject", "roa/hook.js", "roa/frida-inject.sha256", "roa/hook.sha256", "roa/FRIDA-COPYING.txt",
            "patch/display/ActivityRecord-getOrientation.original.smali", "patch/display/ActivityRecord-layoutLetterbox.original.smali", "patch/display/ActivityRecord-getRequestedOrientation.original.smali", "patch/display/ActivityRecord-resolveOverrideConfiguration.original.smali", "patch/display/WindowState-applyGravityAndUpdateFrame.original.smali", "patch/display/helpers/VoyahIranDisplayPolicy$1$1.smali", "patch/display/helpers/VoyahIranDisplayPolicy$1.smali", "patch/display/helpers/VoyahIranDisplayPolicy$2.smali", "patch/display/helpers/VoyahIranDisplayPolicy.smali", "patch/display/legacy-center/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-center/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-center/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-center/VoyahIranDisplayPolicy.smali", "patch/display/legacy-before-theme/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-before-theme/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-before-theme/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-before-theme/VoyahIranDisplayPolicy.smali", "patch/display/legacy-before-density/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-before-density/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-before-density/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-before-density/VoyahIranDisplayPolicy.smali", "patch/display/legacy-global-density/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-global-density/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-global-density/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-global-density/VoyahIranDisplayPolicy.smali", "patch/display/legacy-before-user-density/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-before-user-density/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-before-user-density/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-before-user-density/VoyahIranDisplayPolicy.smali", "patch/FrameworkPatch.java", "patch/original-method.smali", "patch/helper-method.smali", "patch/apktool.jar", "patch/APKTOOL-LICENSE.txt", "patch/THIRD-PARTY.md", "java/bin/java.exe"];
        required = required.Concat(Android9FrameworkAssets.RequiredFiles).ToArray();
        required = required.Concat(new[] { "patch/display/legacy-merge-virtual/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-merge-virtual/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-merge-virtual/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-merge-virtual/VoyahIranDisplayPolicy.smali", "patch/display/legacy-merge-production/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-merge-production/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-merge-production/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-merge-production/VoyahIranDisplayPolicy.smali" }).ToArray();
        required = required.Concat(["patch/display/legacy-welcome-variant/VoyahIranDisplayPolicy.smali", "patch/display/legacy-welcome-variant/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-welcome-variant/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-welcome-variant/VoyahIranDisplayPolicy$2.smali"]).ToArray();
        required = required.Concat(["patch/display/legacy-welcome/VoyahIranDisplayPolicy.smali", "patch/display/legacy-welcome/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-welcome/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-welcome/VoyahIranDisplayPolicy$2.smali"]).ToArray();
        required = required.Concat(required.Where(p => p.StartsWith("patch/display/legacy-merge-production/", StringComparison.Ordinal))
            .Select(p => p.Replace("legacy-merge-production/", "legacy-before-passenger/")))
            .Concat(Android9FrameworkAssets.RequiredFiles.Where(p => p.StartsWith("patch/android9/previous-merge-production/", StringComparison.Ordinal))
            .Select(p => p.Replace("previous-merge-production/", "previous-before-passenger/"))).ToArray();
        if (targetRuntime is "osx-arm64" or "osx-x64")
            return required.Except(new[] { "adb.exe", "AdbWinApi.dll", "AdbWinUsbApi.dll", "libwinpthread-1.dll", "java/bin/java.exe" })
                .Concat(new[] { "adb", "java/bin/java" }).ToArray();
        if (targetRuntime != "win-x64") throw new InvalidDataException("Unsupported payload runtime");
        return required;
    }
    public static PayloadBundle Extract(Stream stream, string root, Stream? commonStream = null)
    {
        using var zip = new ZipArchive(stream, ZipArchiveMode.Read);
        using var common = commonStream == null ? null : new ZipArchive(commonStream, ZipArchiveMode.Read);
        var entries = zip.Entries.Concat(common?.Entries.AsEnumerable() ?? Enumerable.Empty<ZipArchiveEntry>()).ToArray();
        if (entries.GroupBy(e => e.FullName, StringComparer.OrdinalIgnoreCase).Any(g => g.Count() != 1))
            throw new InvalidDataException("Duplicate payload entry");
        using var manifestStream = (zip.GetEntry("manifest.json") ?? throw new InvalidDataException("Payload manifest missing")).Open();
        var manifest = JsonSerializer.Deserialize<BundleManifest>(manifestStream) ?? throw new InvalidDataException("Invalid manifest");
        var required = RequiredFiles(manifest.TargetRuntime);
        if (manifest.PatchSchema != 1 || manifest.Files == null || required.Any(n => !manifest.Files.ContainsKey(n))) throw new InvalidDataException("Unsupported payload schema");
        var names = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var name in manifest.Files.Keys) {
            if (!names.Add(name) || name.Contains('\\') || name.Contains(':') || name.StartsWith('/') || name.Split('/').Any(p => p is ".." or "." or "") ||
                (!required.Contains(name) && !name.StartsWith("java/", StringComparison.Ordinal))) throw new InvalidDataException($"Unsafe payload path: {name}");
        }
        // Split Mac bundles retain a complete per-architecture manifest. Every
        // common byte is checked against it; layers may never shadow each other.
        if (common != null && (!manifest.TargetRuntime.StartsWith("osx-", StringComparison.Ordinal) ||
            entries.Any(e => e.FullName != "manifest.json" && !manifest.Files.ContainsKey(e.FullName))))
            throw new InvalidDataException("Unexpected shared payload entry");
        Directory.CreateDirectory(root);
        foreach (var name in manifest.Files.Keys)
        {
            var entry = zip.GetEntry(name) ?? common?.GetEntry(name) ?? throw new InvalidDataException($"Missing {name}");
            // The application includes offline speech models and OEM launcher animation assets.
            long maxBytes = name == "app.apk" ? 512L * 1024 * 1024 : 256L * 1024 * 1024;
            if (entry.Length > maxBytes) throw new InvalidDataException($"Oversized payload: {name} ({entry.Length} bytes; limit {maxBytes} bytes)");
            var path = Path.Combine(root, name);
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            entry.ExtractToFile(path, false);
            if (Digest(path) != manifest.Files[name]) throw new InvalidDataException($"Payload integrity failed: {name}");
            if (!OperatingSystem.IsWindows() && manifest.TargetRuntime.StartsWith("osx-", StringComparison.Ordinal) &&
                (name == "adb" || name.StartsWith("java/bin/", StringComparison.Ordinal) || name == "java/lib/jspawnhelper"))
                System.IO.File.SetUnixFileMode(path, UnixFileMode.UserRead | UnixFileMode.UserWrite | UnixFileMode.UserExecute);
        }
        return new(root, manifest);
    }
}

