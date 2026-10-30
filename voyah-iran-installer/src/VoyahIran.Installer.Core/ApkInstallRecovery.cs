using System.Diagnostics;
using System.Text.RegularExpressions;

namespace VoyahIran.Installer.Core;

public sealed class ApkInstallRecovery(IAdbClient adb, Action<string> log, Func<Task>? pause = null)
{
    private Task Pause() => pause?.Invoke() ?? Task.Delay(2000);
    public static bool Success(CommandResult result) => result.ExitCode == 0 && result.Output.Split('\n').Any(l => l.Trim() == "Success");
    public static bool Interrupted(CommandResult result) => !Success(result) && new[] {
        "Broken pipe", "DeadObjectException", "DeadSystemException", "device offline", "device not found", "Can't find service: package"
    }.Any(s => result.Output.Contains(s, StringComparison.OrdinalIgnoreCase));

    public async Task<CommandResult> Run(string serial, string transport, string apk, string hash, Func<Task> prepare)
    {
        for (var attempt = 0; ; attempt++)
        {
            var result = await adb.RunAsync(serial, ["install", transport, "-r", "--user", "0", apk], 600);
            if (!Interrupted(result)) return result;
            log($"APK connection interrupted on attempt {attempt+1}: {result.Output}");
            try { await adb.RunAsync(serial, ["logcat", "-d", "-b", "crash", "-t", "2000", "-v", "threadtime"], 15); }
            catch (Exception e) { log("Crash diagnostics unavailable: " + e.Message); }
            log("ارتباط نصب قطع شد؛ منتظر آماده‌شدن همان خودرو و بررسی APK نصب‌شده هستیم.");
            await Ready(serial);
            // Restore root/remount, verify firmware and quiesce the app again before any writes.
            await prepare();
            var paths = await adb.RunAsync(serial, ["shell", "pm", "path", "--user", "0", InstallAppGuard.Package], 15);
            if (paths.ExitCode != 0)
            {
                var list=await adb.RunAsync(serial,["shell","pm","list","packages","--user","0",InstallAppGuard.Package],15);
                if(list.ExitCode!=0 || list.Output.Split('\n').Any(l=>l.Trim()=="package:"+InstallAppGuard.Package) ||
                    list.Output.Split('\n').Any(l=>l.Trim().Length>0 && !l.Trim().StartsWith("package:")))
                    throw new IOException("وضعیت APK پس از اتصال مجدد مشخص نشد؛ نصب تکرار نشد.");
                paths=new CommandResult(0, "");
            }
            var installed = paths.Output.Split('\n').Select(l => l.Trim()).Where(l => l.StartsWith("package:")).ToArray();
            if (installed.Length > 1) throw new IOException("بستهٔ نصب‌شده چندبخشی است؛ تکرار خودکار متوقف شد.");
            if (installed.Length == 1)
            {
                var path=installed[0][8..];
                if (!Regex.IsMatch(path, @"^/[A-Za-z0-9/._=+~\-]+$")) throw new IOException("مسیر APK معتبر نیست.");
                var digest=await adb.RunAsync(serial,["shell","sha256sum",path],30);
                var actual=digest.Output.Split((char[]?)null,StringSplitOptions.RemoveEmptyEntries).FirstOrDefault() ?? "";
                if(digest.ExitCode!=0 || !Regex.IsMatch(actual,@"^[a-fA-F0-9]{64}$")) throw new IOException("هش APK خوانده نشد؛ نصب تکرار نشد.");
                if(actual.Equals(hash,StringComparison.OrdinalIgnoreCase))
                {
                    log("APK با هش مورد انتظار نصب شده است؛ ادامهٔ مراحل بدون نصب مجدد.");
                    return new CommandResult(0,"Success");
                }
            }
            else if (!string.IsNullOrWhiteSpace(paths.Output)) throw new IOException("پاسخ وضعیت APK نامعتبر است؛ نصب تکرار نشد.");
            if(attempt>=1) return result;
            log("APK مورد انتظار نصب نشده؛ یک بار دیگر نصب انجام می‌شود.");
        }
    }
    private async Task Ready(string serial)
    {
        var clock=Stopwatch.StartNew();
        string? previous=null;
        while(clock.Elapsed < TimeSpan.FromMinutes(3))
        {
            await Pause();
            try
            {
                var boot=await adb.RunAsync(serial,["shell","getprop","sys.boot_completed"],5,true);
                var pid=await adb.RunAsync(serial,["shell","pidof","system_server"],5,true);
                var packages=await adb.RunAsync(serial,["shell","pm","path","android"],5,true);
                var settings=await adb.RunAsync(serial,["shell","settings","get","global","device_provisioned"],5,true);
                var identity=await adb.RunAsync(serial,["shell","cat","/proc/sys/kernel/random/boot_id"],5,true);
                var ready=boot.ExitCode==0 && boot.Output.Trim()=="1" && pid.ExitCode==0 && Regex.IsMatch(pid.Output.Trim(),@"^[1-9]\d*$") &&
                    packages.ExitCode==0 && packages.Output.Trim().StartsWith("package:/") && settings.ExitCode==0 &&
                    identity.ExitCode==0 && Guid.TryParse(identity.Output.Trim(),out _);
                var current=pid.Output.Trim()+":"+identity.Output.Trim();
                if(ready && current==previous) return;
                previous=ready?current:null;
            }
            catch(Exception e) when(e is IOException or TimeoutException) { previous=null; }
        }
        throw new TimeoutException("خودرو در سه دقیقه آماده نشد؛ نصب دوباره اجرا نشد.");
    }
}
