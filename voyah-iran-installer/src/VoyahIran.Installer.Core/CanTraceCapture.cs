using System.Diagnostics;
using System.Text;
using System.Text.RegularExpressions;

namespace VoyahIran.Installer.Core;

/// Temporary read-only attachment. No APK, remount, CAN writes or service restart.
public sealed class CanTraceCapture
{
    readonly IAdbClient adb;
    readonly string serial, executable, directory;
    string[] root = [];
    string? targetPid;
    Process? process;
    StreamWriter? writer;
    readonly SemaphoreSlim gate = new(1);
    readonly CancellationTokenSource heartbeat = new();
    Task? heartTask;
    Task? pump;
    readonly Queue<string> startupOutput = new();
    readonly TaskCompletionSource detached = new(TaskCreationOptions.RunContinuationsAsynchronously);
    long receivedBytes;
    volatile bool stopping;
    public long ReceivedBytes => Interlocked.Read(ref receivedBytes);
    readonly TaskCompletionSource ready = new(TaskCreationOptions.RunContinuationsAsynchronously);
    public string FilePath { get; }
    public Task Completion => pump ?? Task.CompletedTask;
    public bool Running => process is { HasExited: false } && ready.Task.IsCompletedSuccessfully;
    CanTraceCapture(IAdbClient adb, string executable, string serial, string file) {
        this.adb=adb; this.executable=executable; this.serial=serial; FilePath=file;
        directory="/data/local/tmp/vi-can-"+Guid.NewGuid().ToString("N");
    }
    static string Quote(string text) => "'"+text.Replace("'", "'\"'\"'")+"'";
    string[] Shell(string command) => new[] {"shell"}.Concat(root).Concat(new[] {"sh", "-c", Quote(command)}).ToArray();
    async Task<string> Run(string command) {
        var r=await adb.RunAsync(serial, Shell(command), 10, quiet:true);
        if(r.ExitCode!=0) throw new IOException(r.Output);
        return r.Output.Trim();
    }
    public static async Task<CanTraceCapture> StartAsync(IAdbClient adb, string executable, string serial, string injector, string output) {
        var c=new CanTraceCapture(adb, executable, serial, output);
        try { await c.Start(injector); return c; }
        catch {
            // Collect evidence before cleanup: target restart, SELinux denial or native crash.
            await c.CaptureFailureEvidenceAsync();
            await c.StopAsync();
            throw;
        }
    }
    async Task Start(string injector) {
        bool found=false;
        foreach(var candidate in new string[][] { [], ["su","0"], ["/system/xbin/su","0"], ["/system/bin/su","0"] }) {
            root=candidate;
            try { if(await Run("id -u")=="0") { found=true; break; } } catch { }
        }
        if(!found) throw new IOException("دسترسی روت در دسترس نیست؛ ابتدا ADB روت خودرو را فعال کنید.");
        if(!(await Run("getprop ro.product.cpu.abilist")).Contains("arm64-v8a"))
            throw new IOException("این ابزار موقت فقط برای arm64 آماده شده است.");
        var pid=await Run("pidof com.qinggan.canbus.service");
        if(!Regex.IsMatch(pid,@"^[1-9][0-9]*$")) throw new IOException("فرایند سرویس CAN به‌صورت یکتا پیدا نشد.");
        // Inspect the process, not only the device. Native transport capture supports ARM32/64
        // and deliberately never initializes the Java bridge that crashed this ARM32 service.
        var elfClass=await Run($"od -An -tu1 -j4 -N1 /proc/{pid}/exe");
        if(elfClass.Trim() is not ("1" or "2"))
            throw new IOException("معماری فرایند CAN قابل تأیید نیست؛ اتصال انجام نشد.");
        targetPid=pid;
        await Run($"mkdir -m 777 {directory}");
        var script=Path.GetTempFileName();
        try {
            using var stream=typeof(CanTraceCapture).Assembly.GetManifestResourceStream("VoyahIran.CanTap.js")!;
            using var reader=new StreamReader(stream);
            await File.WriteAllTextAsync(script,"const VI_DIR = '"+directory+"';\n"+await reader.ReadToEndAsync(), new UTF8Encoding(false));
            foreach(var pair in new[] {(injector,"inject"),(script,"tap.js")}) {
                var result=await adb.RunAsync(serial,["push",pair.Item1,directory+"/"+pair.Item2],60);
                if(result.ExitCode!=0) throw new IOException(result.Output);
            }
        } finally { File.Delete(script); }
        await Run($"chmod 700 {directory}; chmod 755 {directory}/inject; touch {directory}/heartbeat");
        writer=new StreamWriter(new FileStream(FilePath,FileMode.CreateNew,FileAccess.Write,FileShare.Read),new UTF8Encoding(false)){AutoFlush=true};
        await writer.WriteLineAsync($"# CAN native transport capture; host_start={DateTimeOffset.Now:O}; serial={serial}; target_pid={pid}; manual markers are operator estimates, not hardware events");
        var info=new ProcessStartInfo(executable){UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardError=true,StandardOutputEncoding=Encoding.UTF8,StandardErrorEncoding=Encoding.UTF8};
        foreach(var arg in new[]{"-s",serial}.Concat(Shell($"echo $$ > {directory}/pid; exec {directory}/inject -p {pid} -s {directory}/tap.js -i"))) info.ArgumentList.Add(arg);
        process=Process.Start(info) ?? throw new IOException("Cannot start CAN capture");
        pump=Task.Run(Observe);
        heartTask=Task.Run(async () => {
            try { while(!heartbeat.IsCancellationRequested) { await Task.Delay(5000,heartbeat.Token); await Run($"touch {directory}/heartbeat"); } }
            catch { /* Device watchdog detaches when heartbeat is lost. */ }
        });
        try { await ready.Task.WaitAsync(TimeSpan.FromSeconds(20)); }
        catch (TimeoutException) { throw new IOException(await StartupFailure("مهلت آماده‌شدن ابزار شنود تمام شد.")); }
    }
    async Task Observe() {
        async Task Read(StreamReader reader) {
            while(await reader.ReadLineAsync() is {} line) {
                await gate.WaitAsync();
                                try {
                    await writer!.WriteLineAsync(line);
                    if (!ready.Task.IsCompletedSuccessfully) {
                        startupOutput.Enqueue(line.Length > 1200 ? line[..1200] : line);
                        while(startupOutput.Count > 16) startupOutput.Dequeue();
                    }
                } finally { gate.Release(); }
                if (line.StartsWith("VI_CAN_BATCH ") && line.Contains("rx_bytes")) {
                    try {
                        using var json=System.Text.Json.JsonDocument.Parse(line[13..]);
                        foreach(var row in json.RootElement.EnumerateArray())
                            if(row.TryGetProperty("rx_bytes",out var count)) Interlocked.Exchange(ref receivedBytes,count.GetInt64());
                    } catch(System.Text.Json.JsonException) { }
                }
                if (line == "VI_CAN_DETACHED") {
                    detached.TrySetResult();
                    if(!stopping) { try { process!.Kill(true); } catch { } }
                }
                if(line.Contains("VI_CAN_READY")) ready.TrySetResult();
                if(line.Contains("VI_CAN_FAILED")) ready.TrySetException(new IOException(line));
            }
        }
        try { await Task.WhenAll(Read(process!.StandardOutput),Read(process.StandardError)); }
        catch (Exception e) { ready.TrySetException(new IOException(await StartupFailure("خواندن خروجی شنود شکست خورد: " + e.Message), e)); }
        finally {
            if (!ready.Task.IsCompleted) {
                try { await process!.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(2)); } catch { }
                ready.TrySetException(new IOException(await StartupFailure("شنود پیش از آماده شدن متوقف شد.")));
            }
        }
    }
    async Task CaptureFailureEvidenceAsync() {
        if(writer==null || targetPid==null) return;
        foreach(var command in new[] {
            "id; id -Z; getenforce; getprop ro.build.version.sdk; getprop ro.product.cpu.abilist; pidof com.qinggan.canbus.service",
            $"cat /proc/{targetPid}/status; cat /proc/{targetPid}/attr/current",
            "logcat -b crash -d -t 80",
            "logcat -b all -d -t 400"
        }) {
            try {
                var result=await adb.RunAsync(serial,Shell(command),5,quiet:true);
                await gate.WaitAsync();
                try { await writer.WriteLineAsync($"# STARTUP_DIAGNOSTIC {command}\n{result.Output}"); }
                finally { gate.Release(); }
            } catch { }
        }
    }
    async Task<string> StartupFailure(string reason) {
        await gate.WaitAsync();
        try {
            var exit = process?.HasExited == true ? process.ExitCode.ToString() : "running";
            var detail = startupOutput.Count == 0 ? "ابزار هیچ خروجی‌ای نداد." : string.Join(Environment.NewLine, startupOutput);
            return $"{reason}\nExit: {exit}\n{detail}\nگزارش: {FilePath}";
        } finally { gate.Release(); }
    }
    public async Task MarkAsync() {
        if(!Running) throw new IOException("شنود فعال نیست.");
        // Device file timestamp and recorder monotonic timestamp share the car's clocks.
        await Run($"echo {Guid.NewGuid():N}\\|{DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()} >> {directory}/marks");
    }
    public async Task StopAsync() {
        stopping=true;
        if (process?.HasExited == true) await CaptureFailureEvidenceAsync();
        try {
            await Run($"touch {directory}/stop");
            if (process is {HasExited:false}) await detached.Task.WaitAsync(TimeSpan.FromSeconds(3));
        } catch { }
        heartbeat.Cancel();
        if(heartTask!=null) try { await heartTask; } catch { }
        try {
            // Kill only our injector, never the CAN service. Validate process identity before signalling.
            await Run($"if [ -f {directory}/pid ]; then p=$(cat {directory}/pid); case \"$p\" in ''|*[!0-9]*) exit 1;; esac; if tr '\\000' ' ' < /proc/$p/cmdline | grep -F '{directory}/inject' >/dev/null; then kill -TERM $p; fi; fi");
        } catch { }
        if(process!=null) {
            try { await process.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(3)); }
            catch { try { process.Kill(true); } catch { } }
            if(pump!=null) try { await pump; } catch { }
        }
        if(writer!=null) { await writer.WriteLineAsync($"# End host={DateTimeOffset.Now:O}"); await writer.DisposeAsync(); writer=null; }
        try { await Run($"rm -f {directory}/inject {directory}/tap.js {directory}/pid {directory}/mark_* {directory}/marks {directory}/heartbeat {directory}/stop; rmdir {directory}"); } catch { }
        process?.Dispose(); process=null;
    }
}
