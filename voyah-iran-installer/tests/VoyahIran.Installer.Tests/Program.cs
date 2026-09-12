using System.IO.Compression;
using System.Text.Json;
using VoyahIran.Installer.Core;

var root = Path.Combine(Path.GetTempPath(), "VoyahIranInstallerTests", Guid.NewGuid().ToString("N"));
Directory.CreateDirectory(root);
var passed = 0;
void PasswordCase(string instant, string expected) => Check(EngineeringPassword.ForInstant(DateTimeOffset.Parse(instant, System.Globalization.CultureInfo.InvariantCulture)) == expected, "Engineering password: " + instant);
PasswordCase("2026-09-12T00:00:00+08:00", "2938");
PasswordCase("2026-09-14T00:00:00+08:00", "29310");
PasswordCase("2026-12-31T00:00:00+08:00", "3257");
PasswordCase("2026-09-12T19:29:59+03:30", "2938");
PasswordCase("2026-09-12T19:30:00+03:30", "2939");
PasswordCase("2026-12-31T19:30:00+03:30", "2128");
void Check(bool value, string name) { if (!value) throw new Exception("FAIL: " + name); Console.WriteLine("PASS: " + name); passed++; }
Check(DeviceParser.SingleReady(DeviceParser.Parse("List of devices attached\nabc device usb:1 product:x\n"))?.Serial == "abc", "USB ready");
Check(DeviceParser.SingleReady(DeviceParser.Parse("abc unauthorized\n")) == null, "Unauthorized blocked");
Check(DeviceParser.SingleReady(DeviceParser.Parse("abc offline\n")) == null, "Offline blocked");
Check(DeviceParser.SingleReady(DeviceParser.Parse("abc device\nxyz device\n")) == null, "Multiple devices blocked");
Check(DeviceParser.Parse("* daemon started successfully *\nList of devices attached\n").Count == 0, "ADB daemon noise ignored");
using (var log = new SessionLog(Path.Combine(root, "logs")))
{
    log.Hide("secret-value"); log.Write("secret-value remains secret-value");
    Check(!log.ReadText().Contains("secret-value"), "Secret omitted from persisted log");
}
string[] names = ["adb.exe", "AdbWinApi.dll", "AdbWinUsbApi.dll", "libwinpthread-1.dll", "NOTICE.txt", "app.apk", "install-bridge.sh", "bridge.rc", "manage-keyboard-framework.sh", "roa/frida-inject", "roa/hook.js", "roa/frida-inject.sha256", "roa/hook.sha256", "roa/FRIDA-COPYING.txt", "patch/FrameworkPatch.java", "patch/original-method.smali", "patch/helper-method.smali", "patch/apktool.jar", "patch/APKTOOL-LICENSE.txt", "patch/THIRD-PARTY.md", "java/bin/java.exe"];
MemoryStream Fixture(bool corrupt = false)
{
    var memory = new MemoryStream();
    using (var zip = new ZipArchive(memory, ZipArchiveMode.Create, true))
    {
        var hashes = new Dictionary<string, string>();
        foreach (var name in names)
        {
            byte[] bytes = [1, 2, 3];
            hashes[name] = Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(bytes)).ToLowerInvariant();
            using var stream = zip.CreateEntry(name).Open(); stream.Write(corrupt && name == "app.apk" ? [4, 5, 6] : bytes);
        }
        using var manifest = zip.CreateEntry("manifest.json").Open();
        JsonSerializer.Serialize(manifest, new BundleManifest("test", 1, hashes));
    }
    memory.Position = 0; return memory;
}
using var valid = Fixture();
var bundle = PayloadBundle.Extract(valid, Path.Combine(root, "bundle"));
Check(File.Exists(bundle.File("adb.exe")), "Bundle extracted and checked");
try { using var invalid = Fixture(true); PayloadBundle.Extract(invalid, Path.Combine(root, "invalid")); throw new Exception("Corruption accepted"); }
catch (InvalidDataException) { Check(true, "Corrupt embedded APK rejected"); }

foreach (var failure in new[] { "root", "remount", "remount-reboot", "remount-empty", "firmware", "patch", "apk", "bridge", "framework", "framework-transfer", "missing-marker", "timeout" })
{
    using var log = new SessionLog(Path.Combine(root, "engine-logs"));
    var fake = new FakeAdb(failure, bundle);
    var engine = new InstallEngine(fake, bundle, log, Path.Combine(root, "backups"), new FakePatcher(failure));
    var failed = false;
    try { await engine.RunAsync("CAR"); } catch { failed = true; }
    Check(failed && !fake.Calls.Any(c => c.SequenceEqual(new[] { "reboot" })), $"{failure} failure never reboots");
    if (failure is "root" or "remount" or "remount-reboot" or "remount-empty" or "firmware" or "patch") Check(!fake.Calls.Any(c => c[0] == "install"), $"{failure} rejected before APK changes");
    if (failure == "timeout") Check(!fake.Calls.Any(c => c.Contains("rm")), "Uncertain remote write keeps staging");
}
using (var log = new SessionLog(Path.Combine(root, "success-logs")))
{
    var fake = new FakeAdb("none", bundle);
    var engine = new InstallEngine(fake, bundle, log, Path.Combine(root, "backups"), new FakePatcher("none"));
    var phases = new List<PhaseUpdate>(); engine.Progress += phases.Add;
    await engine.RunAsync("CAR");
    Check(phases.Count(p => p.State == PhaseState.Succeeded) == 8, "All eight phases verified on simulated success");
    Check(fake.Calls.Count(c => c.SequenceEqual(new[] { "reboot" })) == 1, "One reboot on success");
    Check(!fake.Calls.Any(c => c.Contains("ime")), "Installer leaves keyboard preference to user");
    var operations = fake.Calls.Select(c => c[0]).ToList();
    Check(operations.IndexOf("root") < operations.IndexOf("wait-for-device") &&
        operations.IndexOf("wait-for-device") < operations.IndexOf("remount") &&
        operations.IndexOf("remount") < operations.IndexOf("install"), "Root and reconnect and remount precede APK installation");
}
foreach (var mode in new[] { "success", "identity-error", "reboot-error" })
{
    using var log = new SessionLog(Path.Combine(root, "reboot-" + mode));
    var fake = new RebootAdb(mode);
    var failed = false;
    try { await new DeviceRebooter(fake, log).RebootAsync("CAR"); } catch (IOException) { failed = true; }
    Check(failed == (mode != "success"), "Manual reboot outcome: " + mode);
    Check(fake.Reboots == (mode == "identity-error" ? 0 : 1), "Reboot is never retried: " + mode);
    Check(fake.Serials.All(s => s == "CAR"), "Reboot stays on selected device: " + mode);
    if (mode == "success") Check(fake.BootChecks == 1, "Reboot confirms completed boot after boot ID changes");
}
Console.WriteLine($"All {passed} checks passed. Fixtures: {root}");

sealed class RebootAdb(string mode) : IAdbClient
{
    public int Reboots;
    public int BootChecks;
    public List<string?> Serials = [];
    public Task<CommandResult> RunAsync(string? serial, IReadOnlyList<string> args, int timeoutSeconds = 30, bool quiet = false)
    {
        Serials.Add(serial);
        if (args[0] == "reboot") { Reboots++; return Task.FromResult(new CommandResult(mode == "reboot-error" ? 1 : 0, "")); }
        if (args.Contains("sys.boot_completed")) { BootChecks++; return Task.FromResult(new CommandResult(0, "1")); }
        return Task.FromResult(new CommandResult(0, mode == "identity-error" ? "bad-id" : Reboots == 0
            ? "00000000-0000-0000-0000-000000000001" : "00000000-0000-0000-0000-000000000002"));
    }
}

sealed class FakeAdb(string failure, PayloadBundle bundle) : IAdbClient
{
    private bool patched;
    public List<IReadOnlyList<string>> Calls { get; } = [];
    public Task<CommandResult> RunAsync(string? serial, IReadOnlyList<string> args, int timeoutSeconds = 30, bool quiet = false)
    {
        Calls.Add(args);
        string text = ""; var exit = 0;
        if (args[0] == "devices") text = "CAR device";
        else if (args[0] == "remount") {
            text = failure == "remount-reboot" ? "remount succeeded; reboot required" : failure == "remount-empty" ? "" : "remount succeeded";
            if (failure == "remount") { exit = 1; text = "remount failed"; }
        }
        else if (args.Contains("id")) text = failure == "root" ? "2000" : "0";
        else if (args.Contains("sha256sum"))
        {
            text = bundle.Manifest.Files["app.apk"];
            if (args.Last().Contains("services.jar") && (patched || args.Last().Contains("voyahiran-stage-"))) text = FakePatcher.PatchedHash;
            if (failure == "firmware") text = "unknown";
            if (failure == "framework-transfer" && args.Last().Contains("voyahiran-stage-")) text = "damaged";
        }
        else if (args[0] == "pull") File.WriteAllBytes(args.Last(), [1,2,3]);
        else if (args.Contains("/proc/sys/kernel/random/boot_id")) text = Calls.Any(c => c[0] == "reboot") ? "00000000-0000-0000-0000-000000000002" : "00000000-0000-0000-0000-000000000001";
        else if (args.Contains("sys.boot_completed")) text = "1";
        else if (args.Contains("init.svc.voyahiran_bridge")) text = "running";
        else if (args.Contains("pm")) text = "package:/data/app/com.abrar.voyahiran/base.apk";
        else if (args[0] == "install") { text = "Success"; if (failure == "apk") exit = 1; }
        else if (args.Any(a => a.EndsWith("payload.sh"))) { text = "VOYAH_BRIDGE_INSTALLED"; if (failure == "bridge") exit = 1; if (failure == "timeout") throw new TimeoutException("Simulated remote timeout"); }
        else if (args.Any(a => a.EndsWith("framework.sh")) && args.Contains("/system/bin/sh")) { patched = true; text = "Keyboard framework installed and verified"; if (failure == "framework") exit = 1; if (failure == "missing-marker") text = ""; }
        return Task.FromResult(new CommandResult(exit, text));
    }
}

sealed class FakePatcher(string failure) : IFrameworkPatcher
{
    public static string PatchedHash => Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(new byte[] {7,8,9})).ToLowerInvariant();
    public Task<FrameworkPatchResult> PatchAsync(string input, string workDirectory)
    {
        if (failure == "patch") throw new InvalidDataException("Unsupported keyboard method");
        Directory.CreateDirectory(workDirectory);
        var output = Path.Combine(workDirectory,"patched.jar"); File.WriteAllBytes(output,[7,8,9]);
        return Task.FromResult(new FrameworkPatchResult(PayloadBundle.Digest(input),PatchedHash,output,false));
    }
}

