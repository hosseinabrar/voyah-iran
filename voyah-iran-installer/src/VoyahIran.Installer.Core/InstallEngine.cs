using System.Security.Cryptography;

namespace VoyahIran.Installer.Core;

public enum InstallPhase { Device, Root, PreparePatch, Apk, Bridge, Framework, Reboot, Verify }
public enum PhaseState { Running, Succeeded, Failed }
public record PhaseUpdate(InstallPhase Phase, PhaseState State);

public sealed class InstallEngine(IAdbClient adb, PayloadBundle bundle, SessionLog log, string backupRoot, IFrameworkPatcher? patcher = null)
{
    public event Action<PhaseUpdate>? Progress;
    private InstallPhase phase;
    private string serial = "";
    private void Begin(InstallPhase next) { phase = next; log.Write($"PHASE {phase}: running"); Progress?.Invoke(new(phase, PhaseState.Running)); }
    private void Done() { log.Write($"PHASE {phase}: succeeded"); Progress?.Invoke(new(phase, PhaseState.Succeeded)); }
    private async Task<string> Command(int timeout, params string[] args)
    {
        var result = await adb.RunAsync(serial, args, timeout);
        if (result.ExitCode != 0) throw new IOException($"فرمان نصب ناموفق بود (کد {result.ExitCode}). جزئیات در گزارش ثبت شده است.");
        return result.Output.Trim();
    }
    private Task<string> Root(int timeout, params string[] args) => Command(timeout, ["shell", "su", "0", .. args]);
    private static string FirstWord(string value) => value.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries).FirstOrDefault()?.ToLowerInvariant() ?? "";
    private async Task VerifyHash(string remote, string expected)
    {
        var actual = FirstWord(await Root(30, "sha256sum", remote));
        if (actual != expected) throw new InvalidDataException($"هش فایل خودرو مطابق انتظار نیست: {remote}; expected={expected}; actual={actual}");
    }
    private async Task PrepareWritableSystem()
    {
        for (var attempt = 0; attempt < 2; attempt++)
        {
            log.Write($"Preparing writable system; attempt={attempt + 1}: adb root, reconnect, UID, remount.");
            await Command(20, "root");
            await Command(45, "wait-for-device");
            if (await Command(15, "shell", "id", "-u") != "0")
                throw new IOException("سرویس ADB با دسترسی روت آماده نشد؛ نصب متوقف شد.");
            // Read output even on nonzero exit: first-time overlay/verity setup can request a reboot.
            var result = await adb.RunAsync(serial, ["remount"], 45);
            var output = result.Output;
            var rebootRequired = output.Contains("reboot", StringComparison.OrdinalIgnoreCase);
            log.Write($"Remount attempt={attempt + 1}; exit={result.ExitCode}; output={output}");
            if (result.ExitCode == 0 && !rebootRequired &&
                output.Contains("remount succeeded", StringComparison.OrdinalIgnoreCase) &&
                !output.Contains("failed", StringComparison.OrdinalIgnoreCase))
            {
                log.Write("ADB root and remount verified on selected device.");
                return;
            }
            if (rebootRequired && attempt == 0)
            {
                log.Write("برای آماده‌سازی سیستم، دستگاه راه‌اندازی مجدد می‌شود؛ کابل را جدا نکنید. نصب خودکار ادامه پیدا می‌کند.");
                await new DeviceRebooter(adb, log).RebootAsync(serial, "Initial remount preparation");
                log.Write("Preparation reboot verified; reapplying root and remount before installing any payload.");
                continue;
            }
            throw new IOException("قابل‌نوشتن شدن سیستم تأیید نشد. گزارش ریمانت را بررسی کنید؛ ریبوت آماده‌سازی بیش از یک بار انجام نمی‌شود.");
        }
    }
    public async Task RunAsync(string deviceSerial)
    {
        serial = deviceSerial;
        var stage = "/data/local/tmp/voyahiran-stage-" + Guid.NewGuid().ToString("N");
        var prefix = stage + "/payload";
        var tokenPath = bundle.File("session.token");
        var stageCreated = false;
        var uncertain = false;
        try
        {
            Begin(InstallPhase.Device);
            var discovery = await adb.RunAsync(null, ["devices", "-l"]);
            var ready = DeviceParser.SingleReady(DeviceParser.Parse(discovery.Output));
            if (discovery.ExitCode != 0 || ready?.Serial != serial) throw new IOException("اتصال خودرو تغییر کرده است. فقط همین خودرو را متصل نگه دارید.");
            Done();
            Begin(InstallPhase.Root);
            await PrepareWritableSystem();
            if (await Root(15, "id", "-u") != "0") throw new IOException("دسترسی روت خودرو فراهم نیست. نصب انجام نشد.");
            await Command(15, "shell", "getprop", "ro.build.fingerprint");
            var currentHash = FirstWord(await Root(30, "sha256sum", "/system/framework/services.jar"));
            if (!System.Text.RegularExpressions.Regex.IsMatch(currentHash, "^[0-9a-f]{64}$")) throw new InvalidDataException("هش فایل سیستم خوانده نشد.");
            var bootId = await Command(15, "shell", "cat", "/proc/sys/kernel/random/boot_id");
            if (!Guid.TryParse(bootId, out _)) throw new InvalidDataException("شناسهٔ راه‌اندازی خودرو خوانده نشد.");
            var directory = Path.Combine(backupRoot, DateTime.Now.ToString("yyyyMMdd-HHmmss") + "-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(directory);
            var backup = Path.Combine(directory, "services.jar");
            await Command(90, "pull", "/system/framework/services.jar", backup);
            if (PayloadBundle.Digest(backup) != currentHash) throw new InvalidDataException("بررسی بکاپ لپ‌تاپ ناموفق بود.");
            log.Write($"Verified current vehicle JAR backup: {backup}");
            Done();
            Begin(InstallPhase.PreparePatch);
            var patch = await (patcher ?? new JavaFrameworkPatcher(bundle, log)).PatchAsync(backup, Path.Combine(directory, "patch"));
            if (patch.OriginalHash != currentHash) throw new InvalidDataException("Patch input hash differs from vehicle backup");
            await File.WriteAllTextAsync(Path.Combine(directory, "patch-result.json"), System.Text.Json.JsonSerializer.Serialize(patch, new System.Text.Json.JsonSerializerOptions { WriteIndented = true }));
            if (patch.AlreadyPatched) log.Write("The exact supported keyboard patch is already present; framework replacement will be skipped.");
            // Detect concurrent firmware changes before installing any app files.
            await VerifyHash("/system/framework/services.jar", currentHash);
            Done();
            Begin(InstallPhase.Apk);
            // Transfer first, then let PackageManager install the local APK. Streaming
            // installation on some head units aborts without returning a package error.
            log.Write("Installing APK without streaming; transfer and package installation are separate. Keep USB connected.");
            var apkResult = await adb.RunAsync(serial,
                ["install", "--no-streaming", "-r", "--user", "0", bundle.File("app.apk")], 600);
            if (apkResult.ExitCode != 0 || !apkResult.Output.Split('\n').Any(l => l.Trim() == "Success"))
            {
                log.Write($"APK install failed: exit={apkResult.ExitCode}; output={apkResult.Output}");
                // Read-only diagnostics; never uninstall the previous app or clear its data.
                try
                {
                    await adb.RunAsync(null, ["devices", "-l"], 15);
                    await adb.RunAsync(serial, ["shell", "df", "-h", "/data"], 15);
                    await adb.RunAsync(serial, ["logcat", "-d", "-b", "all", "-t", "300", "-v", "threadtime"], 20);
                }
                catch (Exception diagnosticError) { log.Write($"APK failure diagnostics unavailable: {diagnosticError.Message}"); }
                throw new IOException($"نصب APK ناموفق بود (کد {apkResult.ExitCode}). اتصال USB و گزارش نصب را بررسی کنید.\n{apkResult.Output.Trim()}");
            }
            Done();
            Begin(InstallPhase.Bridge);
            await Root(15, "mkdir", "-m", "700", stage); stageCreated = true;
            await Root(15, "chown", "2000:2000", stage);
            var token = Convert.ToHexString(RandomNumberGenerator.GetBytes(32)).ToLowerInvariant();
            log.Hide(token);
            await File.WriteAllTextAsync(tokenPath, token);
            await Command(30, "push", tokenPath, prefix + ".token");
            await Root(15, "chmod", "600", prefix + ".token");
            await Command(30, "push", bundle.File("bridge.rc"), prefix + ".rc");
            await Command(30, "push", bundle.File("install-bridge.sh"), prefix + ".sh");
            foreach (var (local, suffix) in new[] { ("roa/frida-inject", ".roa-inject"), ("roa/hook.js", ".roa-hook"),
                ("roa/frida-inject.sha256", ".roa-inject.sha256"), ("roa/hook.sha256", ".roa-hook.sha256") })
            {
                await Command(90, "push", bundle.File(local), prefix + suffix);
                await VerifyHash(prefix + suffix, bundle.Manifest.Files[local]);
            }
            var bridge = await Root(120, "/system/bin/sh", prefix + ".sh", prefix, bundle.Manifest.Files["app.apk"]);
            if (!bridge.Contains("VOYAH_BRIDGE_INSTALLED", StringComparison.Ordinal)) throw new IOException("نصب واسط تأیید نشد.");
            Done();
            Begin(InstallPhase.Framework);
            await VerifyHash("/system/framework/services.jar", currentHash);
            if (!patch.AlreadyPatched)
            {
                await Command(60, "push", patch.FilePath, stage + "/services.jar");
                await VerifyHash(stage + "/services.jar", patch.PatchedHash);
                await Command(30, "push", bundle.File("manage-keyboard-framework.sh"), stage + "/framework.sh");
                var framework = await Root(180, "/system/bin/sh", stage + "/framework.sh", "install", stage + "/services.jar", patch.OriginalHash, patch.PatchedHash);
                if (!framework.Contains("Keyboard framework installed and verified", StringComparison.Ordinal) && !framework.Contains("Already installed", StringComparison.Ordinal))
                    throw new IOException("نصب فایل سیستم تأیید نشد؛ خودرو ریبوت نشد.");
            }
            await VerifyHash("/system/framework/services.jar", patch.PatchedHash);
            Done();
            // Remove staging while root is available, before deliberately disconnecting ADB.
            await Cleanup(stage, prefix); stageCreated = false;
            Begin(InstallPhase.Reboot);
            bootId = await Command(15, "shell", "cat", "/proc/sys/kernel/random/boot_id");
            if (!Guid.TryParse(bootId, out _)) throw new InvalidDataException("شناسهٔ خودرو پیش از ریبوت خوانده نشد.");
            await Command(20, "reboot");
            var deadline = DateTime.UtcNow.AddMinutes(5);
            var rebooted = false;
            while (DateTime.UtcNow < deadline)
            {
                await Task.Delay(2500);
                try
                {
                    var identity = await adb.RunAsync(serial, ["shell", "cat", "/proc/sys/kernel/random/boot_id"], 10, true);
                    if (identity.ExitCode != 0 || !Guid.TryParse(identity.Output.Trim(), out _) || identity.Output.Trim() == bootId) continue;
                    var boot = await adb.RunAsync(serial, ["shell", "getprop", "sys.boot_completed"], 10, true);
                    if (boot.ExitCode == 0 && boot.Output.Trim() == "1") { rebooted = true; break; }
                }
                catch (TimeoutException) { log.Write("Waiting for the same device to complete boot; only status reads are repeated."); }
            }
            if (!rebooted) throw new TimeoutException("خودرو در مهلت پنج دقیقه دوباره آماده نشد. اتصال را بررسی کنید و گزارش را نگه دارید.");
            Done();
            Begin(InstallPhase.Verify);
            await VerifyHash("/system/framework/services.jar", patch.PatchedHash);
            await VerifyHash("/data/local/tmp/voyahiran-bridge/bridge.apk", bundle.Manifest.Files["app.apk"]);
            await VerifyHash("/data/local/tmp/voyahiran-bridge/roa/frida-inject", bundle.Manifest.Files["roa/frida-inject"]);
            await VerifyHash("/data/local/tmp/voyahiran-bridge/roa/hook_camera_front_roa_switch.js", bundle.Manifest.Files["roa/hook.js"]);
            var running = false;
            for (var i = 0; i < 15; i++)
            {
                if (await Command(10, "shell", "getprop", "init.svc.voyahiran_bridge") == "running") { running = true; break; }
                await Task.Delay(2000);
            }
            if (!running) throw new IOException("سرویس واسط پس از راه‌اندازی اجرا نشده است.");
            var packages = await Command(20, "shell", "pm", "path", "com.abrar.voyahiran");
            var apk = packages.Split('\n').Select(l => l.Trim()).FirstOrDefault(l => l.StartsWith("package:/", StringComparison.Ordinal))?[8..];
            if (apk == null || apk.Any(c => !(char.IsAsciiLetterOrDigit(c) || "/._-=+~".Contains(c)))) throw new InvalidDataException("مسیر APK نصب‌شده معتبر نیست.");
            await VerifyHash(apk, bundle.Manifest.Files["app.apk"]);
            var launch = await Command(20, "shell", "am", "start", "--user", "0", "-n", "com.abrar.voyahiran/.ui.MainActivity");
            if (launch.Contains("Error", StringComparison.OrdinalIgnoreCase) || launch.Contains("Exception", StringComparison.OrdinalIgnoreCase)) throw new IOException("نصب تأیید شد، اما بازکردن برنامه ناموفق بود.");
            Done(); log.Write("INSTALLATION VERIFIED. No keyboard preference was forced; use the app to select Gboard.");
        }
        catch (Exception e)
        {
            uncertain = e is TimeoutException;
            log.Write($"PHASE {phase}: FAILED\n{e}"); Progress?.Invoke(new(phase, PhaseState.Failed));
            throw;
        }
        finally
        {
            try { if (File.Exists(tokenPath)) File.Delete(tokenPath); }
            catch (Exception e) { log.Write($"Local credential cleanup failed: {e.Message}"); }
            if (stageCreated && !uncertain)
            {
                try { await Cleanup(stage, prefix); } catch (Exception e) { log.Write($"Staging cleanup failed: {e.Message}"); }
            }
            else if (stageCreated) log.Write($"Staging retained because remote completion is uncertain: {stage}");
        }
    }
    private async Task Cleanup(string stage, string prefix)
    {
        await Root(15, "rm", "-f", prefix + ".token", prefix + ".rc", prefix + ".sh", prefix + ".roa-inject", prefix + ".roa-hook", prefix + ".roa-inject.sha256", prefix + ".roa-hook.sha256", stage + "/framework.sh", stage + "/services.jar");
        await Root(15, "rmdir", stage);
    }
}

