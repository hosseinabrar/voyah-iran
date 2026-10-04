using System.IO.Compression;
using System.Security.Cryptography;
using System.Text.Json;

namespace VoyahIran.Installer.Core;

public record BundleManifest(string AppVersion, int PatchSchema, Dictionary<string, string> Files);
public sealed class PayloadBundle
{
    public string Root { get; }
    public BundleManifest Manifest { get; }
    private PayloadBundle(string root, BundleManifest manifest) { Root = root; Manifest = manifest; }
    public string File(string name) => Path.Combine(Root, name);
    public static string Digest(string path)
    {
        using var stream = System.IO.File.OpenRead(path);
        return Convert.ToHexString(SHA256.HashData(stream)).ToLowerInvariant();
    }
    public static PayloadBundle Extract(Stream stream, string root)
    {
        using var zip = new ZipArchive(stream, ZipArchiveMode.Read);
        using var manifestStream = (zip.GetEntry("manifest.json") ?? throw new InvalidDataException("Payload manifest missing")).Open();
        var manifest = JsonSerializer.Deserialize<BundleManifest>(manifestStream) ?? throw new InvalidDataException("Invalid manifest");
        string[] required = ["adb.exe", "AdbWinApi.dll", "AdbWinUsbApi.dll", "libwinpthread-1.dll", "NOTICE.txt", "app.apk", "apps/neshan.apk", "apps/myket.apk", "apps/gboard.apk", "apps/gboard-config.xhdpi.apk", "install-bridge.sh", "bridge.rc", "manage-keyboard-framework.sh",
            "roa/frida-inject", "roa/hook.js", "roa/frida-inject.sha256", "roa/hook.sha256", "roa/FRIDA-COPYING.txt",
            "patch/display/ActivityRecord-getOrientation.original.smali", "patch/display/ActivityRecord-layoutLetterbox.original.smali", "patch/display/ActivityRecord-getRequestedOrientation.original.smali", "patch/display/ActivityRecord-resolveOverrideConfiguration.original.smali", "patch/display/WindowState-applyGravityAndUpdateFrame.original.smali", "patch/display/helpers/VoyahIranDisplayPolicy$1$1.smali", "patch/display/helpers/VoyahIranDisplayPolicy$1.smali", "patch/display/helpers/VoyahIranDisplayPolicy$2.smali", "patch/display/helpers/VoyahIranDisplayPolicy.smali", "patch/display/legacy-center/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-center/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-center/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-center/VoyahIranDisplayPolicy.smali", "patch/display/legacy-before-theme/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-before-theme/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-before-theme/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-before-theme/VoyahIranDisplayPolicy.smali", "patch/display/legacy-before-density/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-before-density/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-before-density/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-before-density/VoyahIranDisplayPolicy.smali", "patch/display/legacy-global-density/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-global-density/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-global-density/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-global-density/VoyahIranDisplayPolicy.smali", "patch/display/legacy-before-user-density/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-before-user-density/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-before-user-density/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-before-user-density/VoyahIranDisplayPolicy.smali", "patch/FrameworkPatch.java", "patch/original-method.smali", "patch/helper-method.smali", "patch/apktool.jar", "patch/APKTOOL-LICENSE.txt", "patch/THIRD-PARTY.md", "java/bin/java.exe"];
        required = required.Concat(Android9FrameworkAssets.RequiredFiles).ToArray();
        if (manifest.PatchSchema != 1 || required.Any(n => !manifest.Files.ContainsKey(n))) throw new InvalidDataException("Unsupported payload schema");
        var names = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var name in manifest.Files.Keys) {
            if (!names.Add(name) || name.Contains('\\') || name.Contains(':') || name.StartsWith('/') || name.Split('/').Any(p => p is ".." or "." or "") ||
                (!required.Contains(name) && !name.StartsWith("java/", StringComparison.Ordinal))) throw new InvalidDataException($"Unsafe payload path: {name}");
        }
        Directory.CreateDirectory(root);
        foreach (var name in manifest.Files.Keys)
        {
            var entry = zip.GetEntry(name) ?? throw new InvalidDataException($"Missing {name}");
            // The application includes offline speech models and OEM launcher animation assets.
            long maxBytes = name == "app.apk" ? 512L * 1024 * 1024 : 256L * 1024 * 1024;
            if (entry.Length > maxBytes) throw new InvalidDataException($"Oversized payload: {name} ({entry.Length} bytes; limit {maxBytes} bytes)");
            var path = Path.Combine(root, name);
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            entry.ExtractToFile(path, false);
            if (Digest(path) != manifest.Files[name]) throw new InvalidDataException($"Payload integrity failed: {name}");
        }
        return new(root, manifest);
    }
}

