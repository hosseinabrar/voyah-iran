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
if (args.Length == 2 && args[0] == "--verify-payload") {
    try {
        using var payload = File.OpenRead(args[1]);
        var extracted = PayloadBundle.Extract(payload, Path.Combine(root, "actual-payload"));
        Console.WriteLine($"PASS: Actual payload extracted and all {extracted.Manifest.Files.Count} file hashes verified");
    } finally { Directory.Delete(root, true); }
    return;
}
await LogcatCaptureTests.Run(root);
await InstallationStorageTests.Run(root);
using (var removalLog = new SessionLog(Path.Combine(root, "removal"))) await RemovalTests.Run(removalLog);
var passed = 0;
Check(Android9Firmware.IsValidVdexHash("a9303263150cc12ee9e941c9d295a3bf0aa8282284b9c5b15adc6efd59c41c06"), "Original H97A hash accepted");
Check(Android9Firmware.IsValidVdexHash("91ae620de745e2f39a5ff59753cb286be6711e5e6a28da4ce1292c7142b8c68a"), "New H97A revision proceeds to structural verification");
Check(!Android9Firmware.IsValidVdexHash("not-a-hash"), "Malformed VDEX hash rejected");
Check(!Android9Firmware.IsValidVdexHash(""), "Missing VDEX hash rejected");
Check(VehicleInstallProfile.Detect("28", "qti/f0506h:9", "jenkins-PRJ_H97a_8155_OTA_1010-9").LegacyFramework, "H97A selects verified Android 9 reconstruction, not Android 11 patch");
Check(VehicleInstallProfile.Detect("30", "h97x", "").PatchFramework, "Android 11 retains exact patch verification");
const string f0506hFingerprint="qti/f0506h/f0506h:9/PQ3B.190801.002/35:user/jenkins-35,test-keys";
foreach (var fingerprints in new[] { (f0506hFingerprint,f0506hFingerprint), (f0506hFingerprint,""), ("",f0506hFingerprint) }) {
    var profile=VehicleInstallProfile.Detect("28",fingerprints.Item1,fingerprints.Item2);
    Check(profile.LegacyFramework && profile.StreamApk && profile.PatchFramework,
        "H97A f0506h build 35 retains verified legacy framework and streaming installation");
}
foreach (var fingerprint in new[] { "qti/f0506hx/f0506h:9/test", "qti/f0506h/other:9/test", "other/f0506h/f0506h:9/test", "qti/f0506h/f0506h:10/test", "unknown f0506h" }) {
    bool rejected=false;
    try { VehicleInstallProfile.Detect("28",fingerprint,""); } catch(InvalidDataException) { rejected=true; }
    Check(rejected,"Unverified product identity rejected: "+fingerprint);
}
foreach (var sdk in new[] { "", "27", "29", "31", "28" }) {
    bool rejected=false;
    try { VehicleInstallProfile.Detect(sdk, "unknown", ""); } catch(InvalidDataException) { rejected=true; }
    Check(rejected, "Unknown platform rejected before writes: " + sdk);
}

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
var previewIds = LauncherInstallPreparation.PreviewDisplays("mBaseDisplayInfo=DisplayInfo{\"VoyahIran.MapPreview\", displayId 7, owner com.abrar.voyahiran (uid 10055)}");
Check(previewIds.SetEquals(new[] { 7 }), "Only owned preview display selected");
var previewPackages = LauncherInstallPreparation.PreviewPackages("Stack id=1 bounds=[0,0][1920,720] displayId=0 userId=0\n taskId=2: user.central/.Main\nStack id=3 bounds=[0,0][800,600] displayId=7 userId=0\n taskId=4: org.rajman.neshan/.Main\nStack id=5 bounds=[0,0][1920,720] displayId=1 userId=0\n taskId=6: user.passenger/.Main", previewIds);
Check(previewPackages.SetEquals(new[] { "org.rajman.neshan" }), "Cleanup excludes central and passenger applications");
foreach (var anchor in new[] { "com.abrar.voyahiran.feature.launcher.LauncherMapAnchorActivity", ".feature.launcher.LauncherMapAnchorActivity" })
    Check(LauncherInstallPreparation.PreviewPackages("Stack id=1634 displayId=7\n taskId=1634: com.abrar.voyahiran/" + anchor + " bounds=[0,0][871,654] visible=false", previewIds).Count == 0, "Owned anchor is retained until owner termination");
foreach (var invalid in new[] { "com.abrar.voyahiran/.OtherActivity", "com.abrar.voyahiran/.feature.launcher.LauncherMapAnchorActivityExtra", "???" }) {
    var rejected = false;
    try { LauncherInstallPreparation.PreviewPackages("Stack id=8 displayId=7\n taskId=8: " + invalid, previewIds); } catch (IOException) { rejected = true; }
    Check(rejected, "Unknown preview task remains fail-closed: " + invalid);
}
var badDisplayRejected = false;
try { LauncherInstallPreparation.PreviewDisplays("DisplayInfo{\"VoyahIran.MapPreview\", displayId 0, owner other.app}"); }
catch (IOException) { badDisplayRejected = true; }
Check(badDisplayRejected, "Unverified display cannot trigger application shutdown");
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
string[] names = ["adb.exe", "AdbWinApi.dll", "AdbWinUsbApi.dll", "libwinpthread-1.dll", "NOTICE.txt", "app.apk", "apps/neshan.apk", "apps/myket.apk", "apps/gboard.apk", "apps/gboard-config.xhdpi.apk", "install-bridge.sh", "bridge.rc", "manage-keyboard-framework.sh", "roa/frida-inject", "roa/hook.js", "roa/frida-inject.sha256", "roa/hook.sha256", "roa/FRIDA-COPYING.txt", "patch/display/ActivityRecord-getOrientation.original.smali", "patch/display/ActivityRecord-layoutLetterbox.original.smali", "patch/display/ActivityRecord-getRequestedOrientation.original.smali", "patch/display/ActivityRecord-resolveOverrideConfiguration.original.smali", "patch/display/WindowState-applyGravityAndUpdateFrame.original.smali", "patch/display/helpers/VoyahIranDisplayPolicy$1$1.smali", "patch/display/helpers/VoyahIranDisplayPolicy$1.smali", "patch/display/helpers/VoyahIranDisplayPolicy$2.smali", "patch/display/helpers/VoyahIranDisplayPolicy.smali", "patch/display/legacy-center/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-center/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-center/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-center/VoyahIranDisplayPolicy.smali", "patch/display/legacy-before-theme/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-before-theme/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-before-theme/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-before-theme/VoyahIranDisplayPolicy.smali", "patch/display/legacy-before-density/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-before-density/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-before-density/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-before-density/VoyahIranDisplayPolicy.smali", "patch/display/legacy-global-density/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-global-density/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-global-density/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-global-density/VoyahIranDisplayPolicy.smali", "patch/display/legacy-before-user-density/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-before-user-density/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-before-user-density/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-before-user-density/VoyahIranDisplayPolicy.smali", "patch/FrameworkPatch.java", "patch/original-method.smali", "patch/helper-method.smali", "patch/apktool.jar", "patch/APKTOOL-LICENSE.txt", "patch/THIRD-PARTY.md", "java/bin/java.exe"];
names = names.Concat(Android9FrameworkAssets.RequiredFiles).ToArray();
        names = names.Concat(new[] { "patch/display/legacy-merge-virtual/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-merge-virtual/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-merge-virtual/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-merge-virtual/VoyahIranDisplayPolicy.smali", "patch/display/legacy-merge-production/VoyahIranDisplayPolicy$1$1.smali", "patch/display/legacy-merge-production/VoyahIranDisplayPolicy$1.smali", "patch/display/legacy-merge-production/VoyahIranDisplayPolicy$2.smali", "patch/display/legacy-merge-production/VoyahIranDisplayPolicy.smali" }).ToArray();
names = names.Concat(new[] { "legacy-welcome", "legacy-welcome-variant" }.SelectMany(dir => new[] { "VoyahIranDisplayPolicy.smali", "VoyahIranDisplayPolicy$1.smali", "VoyahIranDisplayPolicy$1$1.smali", "VoyahIranDisplayPolicy$2.smali" }.Select(file => "patch/display/" + dir + "/" + file))).Distinct().ToArray();
names = names.Concat(names.Where(p => p.StartsWith("patch/display/legacy-merge-production/", StringComparison.Ordinal))
    .Select(p => p.Replace("legacy-merge-production/", "legacy-before-passenger/")))
    .Concat(Android9FrameworkAssets.RequiredFiles.Where(p => p.StartsWith("patch/android9/previous-merge-production/", StringComparison.Ordinal))
    .Select(p => p.Replace("previous-merge-production/", "previous-before-passenger/"))).Distinct().ToArray();
names = names.Concat(names.Where(p => p.StartsWith("patch/display/helpers/", StringComparison.Ordinal))
    .Select(p => p.Replace("display/helpers/", "display/legacy-passenger-inset/"))).Distinct().ToArray();
names = names.Concat(names.Where(p => p.StartsWith("patch/display/helpers/", StringComparison.Ordinal))
    .SelectMany(p => new[] { p.Replace("display/helpers/", "display/legacy-before-map-theme/"), p.Replace("display/helpers/", "display/legacy-before-huge/") })).Distinct().ToArray();
var sourceRoot = new DirectoryInfo(AppContext.BaseDirectory);
while (sourceRoot != null && !Directory.Exists(Path.Combine(sourceRoot.FullName, "tools", "patch", "android9"))) sourceRoot = sourceRoot.Parent;
if (sourceRoot != null)
{
    var patchRoot = Path.Combine(sourceRoot.FullName, "tools", "patch", "android9");
    var packaged = Directory.GetFiles(patchRoot).Where(p => Path.GetFileName(p) == "Android9Patch.java" ||
        new[] { ".smali", ".jar", ".json" }.Contains(Path.GetExtension(p)) || Path.GetFileName(p).EndsWith("LICENSE.txt"))
        .Concat(new[] { "helpers", "previous-before-huge", "previous-v1", "previous-v2", "previous-v3", "previous-v4", "previous-v5", "previous-v6", "previous-welcome", "previous-welcome-variant", "previous-merge-virtual", "previous-merge-production" }.SelectMany(dir => Directory.GetFiles(Path.Combine(patchRoot, dir), "*.smali", SearchOption.AllDirectories)))
        .Select(p => "patch/android9/" + Path.GetRelativePath(patchRoot, p).Replace('\\', '/')).ToHashSet();
    Check(packaged.SetEquals(Android9FrameworkAssets.RequiredFiles), "Actual Android 9 payload files exactly match extraction allowlist");
}
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
Check(File.Exists(bundle.File("patch/android9/previous-v1/helpers/com/android/server/am/VoyahIranLegacyDisplay.smali")), "Previous display patch upgrade fixture extracted");
foreach (var unexpected in new[] { "patch/android9/previous-v1/unknown.smali", "../escape.txt" })
{
    var saved = names;
    names = names.Append(unexpected).ToArray();
    try { using var invalidPath = Fixture(); PayloadBundle.Extract(invalidPath, Path.Combine(root, Guid.NewGuid().ToString("N"))); throw new Exception("Unsafe path accepted"); }
    catch (InvalidDataException e) { Check(e.Message.Contains(unexpected), "Unknown/path-traversal payload rejected with offending path"); }
    finally { names = saved; }
}
try { using var invalid = Fixture(true); PayloadBundle.Extract(invalid, Path.Combine(root, "invalid")); throw new Exception("Corruption accepted"); }
catch (InvalidDataException) { Check(true, "Corrupt embedded APK rejected"); }

foreach (var failure in new[] { "root", "remount", "remount-empty", "firmware", "patch", "apk", "apk-timeout", "bridge", "framework", "framework-transfer", "missing-marker", "timeout" })
{
    using var log = new SessionLog(Path.Combine(root, "engine-logs"));
    var fake = new FakeAdb(failure, bundle);
    var engine = new InstallEngine(fake, bundle, log, Path.Combine(root, "backups"), new FakePatcher(failure));
    var failed = false;
    try { await engine.RunAsync("CAR"); } catch { failed = true; }
    Check(failed && !fake.Calls.Any(c => c.SequenceEqual(new[] { "reboot" })), $"{failure} failure never reboots");
    Check(!fake.Calls.Any(c=>c.Contains(InstallationStorage.RemoteCleanup)), $"{failure} retains rollback backups");
    if (failure is "root" or "remount" or "remount-reboot" or "remount-empty" or "firmware" or "patch") Check(!fake.Calls.Any(c => c[0] == "install"), $"{failure} rejected before APK changes");
    if (failure == "timeout") Check(!fake.Calls.Any(c => c.Contains("rm")), "Uncertain remote write keeps staging");
    if (failure is "apk" or "apk-timeout") Check(fake.Calls.Count(c => c[0] == "install") == 1, "Do not retry ambiguous or unrelated APK failure");
}
foreach (var mode in new[] { "preview", "preview-stuck" }) {
    using var prepLog = new SessionLog(Path.Combine(root, mode));
    var fake = new FakeAdb(mode, bundle);
    var failed = false;
    try { await new InstallEngine(fake, bundle, prepLog, Path.Combine(root, mode+"-backup"), new FakePatcher("none")).RunAsync("CAR"); } catch { failed = true; }
    var mapStop = fake.Calls.FindIndex(c => c.Contains("force-stop") && c.Contains("org.rajman.neshan"));
    var appStop = fake.Calls.FindIndex(c => c.Contains("force-stop") && c.Contains("com.abrar.voyahiran"));
    Check(mapStop >= 0, "Embedded map stopped before display owner: " + mode);
    Check(mode == "preview" ? !failed && appStop > mapStop : failed && appStop == -1 && !fake.Calls.Any(c=>c[0]=="install"), "Preview cleanup verified before owner termination: " + mode);
    Check(fake.Calls.Any(c=>c.Count == 2 && c[0] == "shell" && c[1].StartsWith("for p in $(pidof com.abrar.voyahiran);") && c[1].Contains("kill -CONT 2749")), "Suspension cleaned up through one remote shell script: " + mode);
}
foreach (var failure in new[] { "apk-storage-transient", "apk-storage-persistent" })
{
    using var log = new SessionLog(Path.Combine(root, "storage-retry-logs"));
    var fake = new FakeAdb(failure, bundle);
    var engine = new InstallEngine(fake, bundle, log, Path.Combine(root, "backups"), new FakePatcher("none"));
    var failed = false;
    try { await engine.RunAsync("CAR"); } catch { failed = true; }
    Check(failed, "Internal storage service failure stops installation");
    Check(fake.Calls.Count(c => c[0] == "install") == 1, "Never repeat install into a broken storage service");
    Check(!fake.Calls.Any(c => c.Contains("uninstall") || c.Contains("clear")), "Storage recovery preserves app data");
    Check(fake.Calls.Count(c => c.SequenceEqual(new[] { "reboot" })) == (failed ? 0 : 1), "No reboot after persistent APK failure");
}
using (var log = new SessionLog(Path.Combine(root, "success-logs")))
{
    var fake = new FakeAdb("none", bundle);
    var engine = new InstallEngine(fake, bundle, log, Path.Combine(root, "backups"), new FakePatcher("none"));
    var phases = new List<PhaseUpdate>(); engine.Progress += phases.Add;
    await engine.RunAsync("CAR");
    Check(phases.Count(p => p.State == PhaseState.Succeeded) == 8, "All eight phases verified on simulated success");
    var cleanupIndex=fake.Calls.FindIndex(c=>c.Contains(InstallationStorage.RemoteCleanup));
    var launchIndex=fake.Calls.FindIndex(c=>c.Contains("com.abrar.voyahiran/.ui.MainActivity"));
    Check(cleanupIndex>launchIndex && launchIndex>=0,"Backup cleanup only after reboot verification and launch");
    Check(!Directory.EnumerateDirectories(Path.Combine(root,"backups")).Any(),"Successful install removes old failed and current local patch generations");
    Check(fake.Calls.Count(c => c.SequenceEqual(new[] { "reboot" })) == 1, "One reboot on success");
    Check(!fake.Calls.Any(c => c.Contains("ime")), "Installer leaves keyboard preference to user");
    Check(fake.Calls.Single(c => c[0] == "install").Contains("--no-streaming"), "Android 11 retains push installation");
    var installIndex = fake.Calls.FindIndex(c => c[0] == "install");
    var stopIndex = fake.Calls.FindIndex(c => c.SequenceEqual(new[] { "shell", "/system/bin/am", "force-stop", "--user", "0", "com.abrar.voyahiran" }));
    var leaseIndex = fake.Calls.FindIndex(c => c.SequenceEqual(new[] { "shell", "/system/bin/settings", "put", "global", "voyah_iran_launcher_lease", "0" }));
    var lastLeaseIndex = fake.Calls.FindLastIndex(c => c.SequenceEqual(new[] { "shell", "/system/bin/settings", "put", "global", "voyah_iran_launcher_lease", "0" }));
    Check(leaseIndex >= 0 && stopIndex > leaseIndex && lastLeaseIndex > stopIndex && installIndex > lastLeaseIndex, "Expire Home hook before stop and after pending heartbeat, before APK replacement");
    var operations = fake.Calls.Select(c => c[0]).ToList();
    Check(operations.IndexOf("root") < operations.IndexOf("wait-for-device") &&
        operations.IndexOf("wait-for-device") < operations.IndexOf("remount") &&
        operations.IndexOf("remount") < operations.IndexOf("install"), "Root and reconnect and remount precede APK installation");
}
using (var log = new SessionLog(Path.Combine(root, "h97a-logs")))
{
    var fake = new FakeAdb("h97a", bundle);
    // Verify the compatible framework stage is reached, with streaming retained.
    await new InstallEngine(fake, bundle, log, Path.Combine(root, "h97a-backup"), new FakePatcher("none")).RunAsync("CAR");
    Check(!fake.Calls.Any(c => c.Contains("su")), "H97A uses verified adbd root without su PATH");
    Check(fake.Calls.Any(c => c.Contains("/system/bin/sh") && c.Any(a => a.EndsWith("framework.sh"))), "H97A installs its verified framework result");
    Check(fake.Calls.Any(c => c[0] == "install"), "H97A installs the compatible APK and bridge");
    Check(fake.Calls.Single(c => c[0] == "install").Contains("--streaming"), "H97A avoids system_server opening shell_data_file");
}
using (var log = new SessionLog(Path.Combine(root, "h97a-unsupported-framework")))
{
    var fake = new FakeAdb("h97a", bundle);
    var rejected = false;
    try { await new InstallEngine(fake, bundle, log, Path.Combine(root, "h97a-rejected"), new FakePatcher("patch")).RunAsync("CAR"); }
    catch (InvalidDataException) { rejected = true; }
    Check(rejected && !fake.Calls.Any(c => c[0] == "install" || c[0] == "push" || c[0] == "reboot"),
        "Unsupported Android 9 patch fails before APK, staging and reboot");
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
    else Check(fake.Calls.FindIndex(c => c[0] == "install") > fake.Calls.FindIndex(fake.Calls.FindIndex(c => c[0] == "pull"), c => c[0] == "wait-for-device"), "Install resumes after reconnect");
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
    Check(fake.Calls.Count(c => c[0] == "root") == (failed ? 2 : 3), "Root reacquired after reboot: " + mode);
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
foreach (var mode in new[] { "ready", "delayed", "race", "missing", "security", "timeout", "empty" })
{
    var fake = new LaunchAdb(mode);
    Exception? error = null;
    try { await PostBootAppLauncher.LaunchAsync(fake, "CAR", _ => { }, () => Task.CompletedTask); }
    catch (Exception e) { error = e; }
    Check((error == null) == (mode is "ready" or "delayed" or "race"), "Post-boot launch " + mode);
    if (mode == "delayed") Check(fake.Queries == 3 && fake.Starts == 1, "Wait for resolution before starting");
    if (mode == "race") Check(fake.Starts == 2, "Retry explicit Error type 3");
    if (mode == "missing") Check(fake.Queries == 10 && fake.Starts == 0, "Bounded unresolved activity wait");
    if (mode is "security" or "timeout" or "empty") Check(fake.Starts == 1, "No ambiguous or permanent launch retry: " + mode);
}
Console.WriteLine($"All {passed} checks passed. Fixtures: {root}");

sealed class LaunchAdb(string mode) : IAdbClient
{
    public int Queries, Starts;
    public Task<CommandResult> RunAsync(string? serial, IReadOnlyList<string> args, int timeoutSeconds = 30, bool quiet = false)
    {
        if (serial != "CAR") throw new Exception("Wrong device");
        if (args.Contains("resolve-activity"))
        {
            Queries++;
            return Task.FromResult(new CommandResult(0, mode == "missing" || mode == "delayed" && Queries < 3
                ? "No activity found" : "com.abrar.voyahiran/.ui.MainActivity"));
        }
        Starts++;
        if (mode == "timeout") throw new TimeoutException();
        return Task.FromResult(new CommandResult(0, mode == "security" ? "SecurityException: denied"
            : mode == "empty" ? "" : mode == "race" && Starts == 1 ? "Error type 3\nActivity does not exist"
            : "Starting: Intent { cmp=com.abrar.voyahiran/.ui.MainActivity }"));
    }
}

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
        if (args[0] == "wait-for-device" && Calls.Any(c => c[0] == "pull") && !Calls.Any(c => c[0] == "reboot"))
        {
            WaitEntered = true; PostPatchWaitSerial = serial; PostPatchWaitTimeout = timeoutSeconds;
            if (failure == "reconnect") return Reconnect.Task;
            if (failure == "reconnect-timeout") throw new TimeoutException("Device absent");
        }
        if (WaitEntered && failure == "reconnect-boot" && args.Contains("/proc/sys/kernel/random/boot_id"))
            return Task.FromResult(new CommandResult(0, Guid.Empty.ToString()));
        if (args.Contains("ro.build.version.sdk")) return Task.FromResult(new CommandResult(0, failure == "h97a" ? "28" : "30"));
        if (args.Contains("ro.vendor.build.fingerprint")) return Task.FromResult(new CommandResult(0, failure == "h97a" ? "jenkins-PRJ_H97a_8155_OTA_1010-9" : "test"));
        if (args.Contains("ro.build.fingerprint"))
            return Task.FromResult(new CommandResult(0, WaitEntered && failure == "reconnect-firmware" ? "changed" : "test-firmware"));
        if (failure.StartsWith("preview")) {
            if (args.Contains("dumpsys") && args.Contains("display")) return Task.FromResult(new CommandResult(0, "DisplayInfo{\"VoyahIran.MapPreview\", displayId 7, owner com.abrar.voyahiran (uid 10055)}"));
            if (args.Contains("pidof")) return Task.FromResult(new CommandResult(0, "2749"));
            if (args.Contains("stack") && args.Contains("list")) return Task.FromResult(new CommandResult(0,
                "Stack id=8 bounds=[0,0][871,654] displayId=7 userId=0\n taskId=8: com.abrar.voyahiran/com.abrar.voyahiran.feature.launcher.LauncherMapAnchorActivity bounds=[0,0][871,654] userId=0 visible=false\n" +
                (failure == "preview-stuck" || !Calls.Any(c=>c.Contains("force-stop") && c.Contains("org.rajman.neshan")) ? "Stack id=3 bounds=[0,0][800,600] displayId=7 userId=0\n taskId=4: org.rajman.neshan/.Main" : "Stack id=1 bounds=[0,0][1920,720] displayId=0 userId=0")));
        }
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
        else if (args.Contains("resolve-activity")) text = "com.abrar.voyahiran/.ui.MainActivity";
        else if (args.Contains("am") && args.Contains("start")) text = "Starting: Intent { cmp=com.abrar.voyahiran/.ui.MainActivity }";
        else if (args.Contains("pm")) text = "package:/data/app/com.abrar.voyahiran/base.apk";
        else if (args[0] == "install") {
            text = "Success"; if (failure == "apk") exit = 1;
            if (failure == "apk-timeout") throw new TimeoutException("Uncertain package write");
            if (failure == "apk-storage-persistent" || (failure == "apk-storage-transient" && Calls.Count(c => c[0] == "install") < 3)) {
                exit = 255;
                text = "java.lang.NullPointerException: Attempt to invoke virtual method 'void android.content.pm.PackageManagerInternal.freeStorage(java.lang.String, long, int)' on a null object reference\n at com.android.server.StorageManagerService.allocateBytes(StorageManagerService.java:4013)\n at com.android.server.pm.PackageInstallerSession.doWriteInternal(PackageInstallerSession.java:1006)";
            }
            if (failure == "h97a" && !args.Contains("--streaming")) {
                exit = 255;
                text = "System server has no access to read file context u:object_r:shell_data_file:s0; Error: Can't open file: /data/local/tmp/app.apk";
            }
        }
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
