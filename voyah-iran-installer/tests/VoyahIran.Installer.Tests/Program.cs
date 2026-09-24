using System.IO.Compression;
using System.Text.Json;
using VoyahIran.Installer.Core;

// Child-process fixture: behaves like a streaming adb logcat, without any device.
if (args.Contains("logcat")) {
    Console.OutputEncoding = System.Text.Encoding.UTF8;
    var markerFile = args[Array.IndexOf(args, "-s") + 1];
    Console.WriteLine("old buffered log");
    var deadline = DateTime.UtcNow.AddSeconds(10);
    while (!File.Exists(markerFile) && DateTime.UtcNow < deadline) await Task.Delay(20);
    if (!File.Exists(markerFile)) return;
    Console.WriteLine("I VoyahIranCapture: " + File.ReadAllText(markerFile));
    Console.WriteLine("گزارش تازه خودرو");
    Console.Error.WriteLine("diagnostic stderr");
    if (File.Exists(markerFile + ".exit")) return;
    await Task.Delay(TimeSpan.FromSeconds(30));
    return;
}
var root = Path.Combine(Path.GetTempPath(), "VoyahIranInstallerTests", Guid.NewGuid().ToString("N"));
Directory.CreateDirectory(root);
await LogcatCaptureTests.Run(root);
using (var removalLog = new SessionLog(Path.Combine(root, "removal"))) await RemovalTests.Run(removalLog);
var passed = 0;
var boundary = new LogcatBoundary("SESSION_UNIQUE_MARKER");
Check(!boundary.Accept("old buffered log"), "Capture excludes old backlog");
Check(!boundary.Accept("unrelated marker"), "Capture waits for its own marker");
Check(boundary.Accept("I VoyahIranCapture: SESSION_UNIQUE_MARKER"), "Capture starts at marker");
Check(boundary.Accept("گزارش جدید خودرو"), "Capture accepts subsequent Unicode logs");

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
string[] names = ["adb.exe", "AdbWinApi.dll", "AdbWinUsbApi.dll", "libwinpthread-1.dll", "NOTICE.txt", "app.apk", "apps/myket.apk", "apps/gboard.apk", "apps/gboard-config.xhdpi.apk", "install-bridge.sh", "bridge.rc", "manage-keyboard-framework.sh", "roa/frida-inject", "roa/hook.js", "roa/frida-inject.sha256", "roa/hook.sha256", "roa/FRIDA-COPYING.txt", "patch/display/ActivityRecord-getOrientation.original.smali", "patch/display/ActivityRecord-layoutLetterbox.original.smali", "patch/display/ActivityRecord-getRequestedOrientation.original.smali", "patch/display/ActivityRecord-resolveOverrideConfiguration.original.smali", "patch/display/WindowState-applyGravityAndUpdateFrame.original.smali", "patch/display/helpers/VoyahIranDisplayPolicy$1$1.smali", "patch/display/helpers/VoyahIranDisplayPolicy$1.smali", "patch/display/helpers/VoyahIranDisplayPolicy$2.smali", "patch/display/helpers/VoyahIranDisplayPolicy.smali", "patch/display/legacy-center/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-center/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-center/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-center/VoyahIranDisplayPolicy.smali", "patch/FrameworkPatch.java", "patch/original-method.smali", "patch/helper-method.smali", "patch/apktool.jar", "patch/APKTOOL-LICENSE.txt", "patch/THIRD-PARTY.md", "java/bin/java.exe"];
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

foreach (var failure in new[] { "root", "remount", "remount-empty", "firmware", "patch", "apk", "bridge", "framework", "framework-transfer", "missing-marker", "timeout" })
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
foreach (var mode in new[] { "reconnect", "reconnect-timeout", "reconnect-firmware", "reconnect-boot" })
{
    using var log = new SessionLog(Path.Combine(root, mode));
    var fake = new FakeAdb(mode, bundle);
    var patch = new FakePatcher("none");
    var engine = new InstallEngine(fake, bundle, log, Path.Combine(root, "backups"), patch);
    var run = engine.RunAsync("CAR");
    if (mode == "reconnect")
    {
        while (!fake.WaitEntered && !run.IsCompleted) await Task.Delay(1);
        Check(fake.WaitEntered && !fake.Calls.Any(c => c[0] == "install"), "No APK install while USB is disconnected");
        fake.Reconnect.TrySetResult(new CommandResult(0, ""));
    }
    var failed = false;
    try { await run; } catch (IOException) { failed = true; }
    Check(failed == (mode != "reconnect"), "Post-patch reconnect outcome: " + mode);
    Check(patch.Calls == 1, "Patch not rebuilt during reconnect: " + mode);
    Check(fake.PostPatchWaitSerial == "CAR" && fake.PostPatchWaitTimeout == 120, "Wait bounded and pinned to original device: " + mode);
    if (failed) Check(!fake.Calls.Any(c => c[0] is "install" or "push" or "reboot"), "Reconnect failure stops before payload writes: " + mode);
    else Check(fake.Calls.FindIndex(c => c[0] == "install") > fake.Calls.FindLastIndex(c => c[0] == "wait-for-device"), "Install resumes after reconnect");
}
foreach (var mode in new[] { "remount-recover", "remount-recover-nonzero", "remount-reboot" })
{
    using var log = new SessionLog(Path.Combine(root, mode));
    var fake = new FakeAdb(mode, bundle);
    var engine = new InstallEngine(fake, bundle, log, Path.Combine(root, "backups"), new FakePatcher("none"));
    var failed = false;
    try { await engine.RunAsync("CAR"); } catch (IOException) { failed = true; }
    Check(failed == (mode == "remount-reboot"), "Remount recovery outcome: " + mode);
    Check(fake.Calls.Count(c => c[0] == "remount") == 2, "Remount retried once: " + mode);
    Check(fake.Calls.Count(c => c[0] == "root") == 2, "Root reacquired after reboot: " + mode);
    Check(fake.Calls.Count(c => c[0] == "reboot") == (failed ? 1 : 2), "Bounded preparation reboot: " + mode);
    if (failed) Check(!fake.Calls.Any(c => c[0] == "install"), "Repeated reboot request stops before APK install");
    else Check(fake.Calls.FindIndex(c => c[0] == "install") > fake.Calls.FindLastIndex(c => c[0] == "remount"), "Install waits for successful remount: " + mode);
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
using (var log = new SessionLog(Path.Combine(root, "myket-permission")))
{
    var fake = new PermissionAdb();
    var installer = new ApkInstaller(fake, log, "CAR");
    var info = new ApkInfo("ir.mservices.market", new HashSet<string> { "android.permission.REQUEST_INSTALL_PACKAGES" });
    var choice = ApkInstaller.Choices.Single(c => c.Names.Contains("REQUEST_INSTALL_PACKAGES"));
    await installer.SetAsync(info, new[] { choice }, true);
    Check(await installer.ReadAsync(info, choice), "Myket install permission granted and read back");
    Check(fake.Writes == 1, "Myket gets only install-packages permission");
}
var appInputs = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../tools/apps"));
if (Directory.Exists(appInputs)) {
    var myket = ApkInfo.Read(Path.Combine(appInputs, "myket.apk"));
    Check(myket.Package == "ir.mservices.market" && myket.Permissions.Contains("android.permission.REQUEST_INSTALL_PACKAGES"), "Real bundled Myket identity and permission");
    Check(ApkInfo.Read(Path.Combine(appInputs, "gboard.apk")).Package == "com.google.android.inputmethod.latin", "Real bundled Gboard identity");
}
var gboardFiles = new[] { Path.Combine(appInputs, "gboard.apk"), Path.Combine(appInputs, "gboard-config.xhdpi.apk") };
var gboardInfo = ApkInfo.Read(gboardFiles[0]);
using (var splitLog = new SessionLog(Path.Combine(root, "splits"))) {
    var success = new SplitAdb("success");
    await new ApkInstaller(success, splitLog, "CAR").InstallSplitAsync(gboardFiles, gboardInfo);
    Check(success.Calls.Count == 2 && success.Calls[0].SequenceEqual(new[] { "install-multiple", "--no-streaming", "-r", "--user", "0" }.Concat(gboardFiles)), "Gboard base and split install atomically for user zero");
    foreach (var failure in new[] { "failure", "missing-split" }) {
        var fake = new SplitAdb(failure); bool rejected = false;
        try { await new ApkInstaller(fake, splitLog, "CAR").InstallSplitAsync(gboardFiles, gboardInfo); }
        catch (InvalidOperationException) { rejected = true; }
        Check(rejected && fake.Calls.Count == (failure == "failure" ? 1 : 2), "Split install rejects " + failure);
    }
    var mismatch = new SplitAdb("success"); bool invalid = false;
    try { await new ApkInstaller(mismatch, splitLog, "CAR").InstallSplitAsync(new[] { gboardFiles[0], Path.Combine(appInputs, "myket.apk") }, gboardInfo); }
    catch (InvalidDataException) { invalid = true; }
    Check(invalid && mismatch.Calls.Count == 0, "Mismatched APK set rejected before device operations");
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
    public TaskCompletionSource<CommandResult> Reconnect { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
    public bool WaitEntered;
    public string? PostPatchWaitSerial;
    public int PostPatchWaitTimeout;
    private bool patched;
    private int remounts;
    public List<IReadOnlyList<string>> Calls { get; } = [];
    public Task<CommandResult> RunAsync(string? serial, IReadOnlyList<string> args, int timeoutSeconds = 30, bool quiet = false)
    {
        Calls.Add(args);
        if (args[0] == "wait-for-device" && Calls.Any(c => c[0] == "pull"))
        {
            WaitEntered = true; PostPatchWaitSerial = serial; PostPatchWaitTimeout = timeoutSeconds;
            if (failure == "reconnect") return Reconnect.Task;
            if (failure == "reconnect-timeout") throw new TimeoutException("Device absent");
        }
        if (WaitEntered && failure == "reconnect-boot" && args.Contains("/proc/sys/kernel/random/boot_id"))
            return Task.FromResult(new CommandResult(0, Guid.Empty.ToString()));
        if (args.Contains("ro.build.fingerprint"))
            return Task.FromResult(new CommandResult(0, WaitEntered && failure == "reconnect-firmware" ? "changed" : "test-firmware"));
        string text = ""; var exit = 0;
        if (args[0] == "devices") text = "CAR device";
        else if (args[0] == "remount") {
            remounts++;
            text = (failure == "remount-reboot" || ((failure == "remount-recover" || failure == "remount-recover-nonzero") && remounts == 1)) ? "remount succeeded; reboot required" : failure == "remount-empty" ? "" : "remount succeeded";
            if (failure == "remount-recover-nonzero" && remounts == 1) exit = 1;
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
        else if (args.Contains("/proc/sys/kernel/random/boot_id")) text = new Guid(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte)(1 + Calls.Count(c => c[0] == "reboot"))).ToString();
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
    public int Calls;
    public static string PatchedHash => Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(new byte[] {7,8,9})).ToLowerInvariant();
    public Task<FrameworkPatchResult> PatchAsync(string input, string workDirectory)
    {
        Calls++;
        if (failure == "patch") throw new InvalidDataException("Unsupported keyboard method");
        Directory.CreateDirectory(workDirectory);
        var output = Path.Combine(workDirectory,"patched.jar"); File.WriteAllBytes(output,[7,8,9]);
        return Task.FromResult(new FrameworkPatchResult(PayloadBundle.Digest(input),PatchedHash,output,false));
    }
}


sealed class PermissionAdb : IAdbClient {
    public int Writes;
    private string mode = "default";
    public Task<CommandResult> RunAsync(string? serial, IReadOnlyList<string> args, int timeoutSeconds=30, bool quiet=false) {
        if (serial != "CAR" || !args.Contains("ir.mservices.market") || !args.Contains("REQUEST_INSTALL_PACKAGES")) throw new Exception("Unexpected permission command");
        if (args.Contains("set")) { Writes++; mode=args.Last(); return Task.FromResult(new CommandResult(0,"")); }
        return Task.FromResult(new CommandResult(0,"REQUEST_INSTALL_PACKAGES: " + mode));
    }
}

sealed class SplitAdb(string mode) : IAdbClient {
    public List<IReadOnlyList<string>> Calls = new();
    public Task<CommandResult> RunAsync(string? serial, IReadOnlyList<string> args, int timeoutSeconds = 30, bool quiet = false) {
        if (serial != "CAR") throw new Exception("Wrong device");
        Calls.Add(args);
        if (args[0] == "install-multiple") return Task.FromResult(new CommandResult(mode == "failure" ? 1 : 0, mode == "failure" ? "Failure" : "Success"));
        if (!args.SequenceEqual(new[] { "shell", "pm", "path", "--user", "0", "com.google.android.inputmethod.latin" })) throw new Exception("Unexpected verification");
        return Task.FromResult(new CommandResult(0, "package:/data/app/gboard/base.apk\n" + (mode == "missing-split" ? "" : "package:/data/app/gboard/split_config.xhdpi.apk")));
    }
}
