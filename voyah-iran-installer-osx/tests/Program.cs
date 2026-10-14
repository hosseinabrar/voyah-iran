using System.IO.Compression;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using VoyahIran.Installer.Core;
using VoyahIran.Installer.Mac;

var root = Path.Combine(Path.GetTempPath(), "voyah-mac-tests-" + Guid.NewGuid().ToString("N"));
Directory.CreateDirectory(root);
var passed = 0;
void Check(bool value, string name) { if (!value) throw new Exception(name); passed++; Console.WriteLine("PASS " + name); }
MemoryStream Payload(string runtime, string? corrupt = null, string? omit = null, string? extra = null, bool duplicate = false) {
    var stream = new MemoryStream();
    using (var zip = new ZipArchive(stream, ZipArchiveMode.Create, true)) {
        var files = new Dictionary<string, string>();
        foreach (var name in PayloadBundle.RequiredFiles(runtime).Append("java/lib/jspawnhelper").Concat(extra == null ? [] : new[] { extra }).Where(n => n != omit)) {
            var bytes = Encoding.UTF8.GetBytes(name == corrupt ? "tampered" : "fixture");
            using (var target = zip.CreateEntry(name).Open()) target.Write(bytes);
            files[name] = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes("fixture"))).ToLowerInvariant();
        }
        if (duplicate) zip.CreateEntry("adb");
        using var manifest = zip.CreateEntry("manifest.json").Open();
        JsonSerializer.Serialize(manifest, new BundleManifest("2.0.3", 1, files, runtime));
    }
    stream.Position = 0; return stream;
}
void Reject(MemoryStream payload, string name) {
    using (payload) {
        bool rejected = false;
        try { PayloadBundle.Extract(payload, Path.Combine(root, Guid.NewGuid().ToString("N"))); }
        catch (InvalidDataException) { rejected = true; }
        Check(rejected, name);
    }
}
try {
    // Reuse complete legacy manifests while distributing bytes across layers.
    foreach (var runtime in new[] { "osx-arm64", "osx-x64" }) {
        foreach (var scenario in new[] { "valid", "tamper", "missing", "duplicate", "unexpected" }) {
            using var original = Payload(runtime);
            using var originalZip = new ZipArchive(original, ZipArchiveMode.Read);
            using var specific = new MemoryStream();
            using var shared = new MemoryStream();
            using (var archZip = new ZipArchive(specific, ZipArchiveMode.Create, true))
            using (var commonZip = new ZipArchive(shared, ZipArchiveMode.Create, true)) {
                foreach (var entry in originalZip.Entries) {
                    if (entry.FullName == "app.apk" && scenario == "missing") continue;
                    var destination = entry.FullName == "app.apk" ? commonZip : archZip;
                    using var output = destination.CreateEntry(entry.FullName).Open();
                    if (entry.FullName == "app.apk" && scenario == "tamper")
                        output.Write(Encoding.UTF8.GetBytes("bad"));
                    else { using var input = entry.Open(); input.CopyTo(output); }
                }
                if (scenario == "duplicate") commonZip.CreateEntry("adb");
                if (scenario == "unexpected") commonZip.CreateEntry("../escape");
            }
            specific.Position = 0; shared.Position = 0;
            bool rejected = false;
            try {
                var bundle = PayloadBundle.Extract(specific, Path.Combine(root, Guid.NewGuid().ToString("N")), shared);
                Check(File.ReadAllText(bundle.File("app.apk")) == "fixture", "Shared APK extracted " + runtime);
            } catch (InvalidDataException) { rejected = true; }
            Check(rejected == (scenario != "valid"), "Split payload " + runtime + " " + scenario);
        }
    }
    foreach (var runtime in new[] { "osx-arm64", "osx-x64", "win-x64" }) {
        using var input = Payload(runtime);
        var bundle = PayloadBundle.Extract(input, Path.Combine(root, runtime));
        Check(bundle.Manifest.TargetRuntime == runtime, "Runtime retained " + runtime);
        Check(File.Exists(bundle.JavaExecutable), "Java resolved " + runtime);
        if (runtime.StartsWith("osx-") && !OperatingSystem.IsWindows()) {
            Check(!File.Exists(bundle.File("adb.exe")), "No Windows ADB in Mac bundle");
            foreach (var executable in new[] { "adb", "java/bin/java", "java/lib/jspawnhelper" })
                Check((File.GetUnixFileMode(bundle.File(executable)) & UnixFileMode.UserExecute) != 0, "Executable mode restored " + executable);
        }
    }
    Reject(Payload("osx-arm64", corrupt: "app.apk"), "Tampered APK rejected");
    Reject(Payload("osx-arm64", omit: "adb"), "Missing Mac ADB rejected");
    Reject(Payload("osx-arm64", extra: "../escape"), "Traversal rejected");
    Reject(Payload("osx-arm64", extra: "unlisted-script.sh"), "Unexpected executable rejected");
    Reject(Payload("osx-arm64", duplicate: true), "Duplicate ZIP names rejected");
    var old = JsonSerializer.Deserialize<BundleManifest>("{\"AppVersion\":\"2.0.3\",\"PatchSchema\":1,\"Files\":{}}")!;
    Check(old.TargetRuntime == "win-x64", "Old Windows manifest remains compatible");
    var fake = new ClockAdb();
    await MacOperations.SetClock(fake, "test-serial");
    Check(fake.Calls.Any(a => a.Contains("set-time")), "Clock write dispatched");
    Check(fake.Calls.All(a => a[0] == "test-serial"), "Clock commands remain pinned to selected serial");
    fake = new ClockAdb { FailTime = true };
    try { await MacOperations.SetClock(fake, "test-serial"); throw new Exception("Expected failure"); }
    catch (IOException) { }
    Check(fake.Calls.Last().Last() == "GMT+03:30", "Timezone restored when time write fails");
    Console.WriteLine($"Mac installer: {passed} checks passed; no vehicle connected.");
} finally { Directory.Delete(root, true); }

sealed class ClockAdb : IAdbClient {
    public bool FailTime;
    public List<string[]> Calls = [];
    public Task<CommandResult> RunAsync(string? serial, IReadOnlyList<string> args, int timeoutSeconds = 30, bool quiet = false) {
        Calls.Add(new[] { serial! }.Concat(args).ToArray());
        var output = args.Contains("getprop") ? "GMT+03:30" : args.Contains("get") ? "0" : args.Contains("date") ? DateTimeOffset.UtcNow.ToUnixTimeSeconds().ToString() : "";
        return Task.FromResult(new CommandResult(FailTime && args.Contains("set-time") ? 1 : 0, output));
    }
}
