using System.Diagnostics;
using System.Text;

namespace VoyahIran.Installer.Core;

/// <summary>Discards old buffered entries until our unique device-side start marker arrives.</summary>
public sealed class LogcatBoundary(string marker)
{
    public bool Started { get; private set; }
    public bool Accept(string line)
    {
        if (!Started && line.Contains(marker, StringComparison.Ordinal)) Started = true;
        return Started;
    }
}
public record LogcatCompletion(int ExitCode, bool RequestedStop, string? Error);

/// <summary>Streams all accessible logcat buffers to disk, never accumulating them in memory.</summary>
public sealed class LogcatCapture : IDisposable
{
    private readonly Process process;
    private readonly StreamWriter writer;
    private readonly object gate = new();
    private readonly LogcatBoundary boundary;
    private readonly TaskCompletionSource readerReady = new(TaskCreationOptions.RunContinuationsAsynchronously);
    private readonly TaskCompletionSource started = new(TaskCreationOptions.RunContinuationsAsynchronously);
    private volatile bool stopping;
    private string? pumpError;
    public string FilePath { get; }
    public Task<LogcatCompletion> Completion { get; }

    private LogcatCapture(string executable, string serial, string filePath, string marker)
    {
        FilePath = filePath;
        boundary = new(marker);
        writer = new(new FileStream(filePath, FileMode.CreateNew, FileAccess.Write, FileShare.Read),
            new UTF8Encoding(false)) { AutoFlush = true };
        var info = new ProcessStartInfo(executable) {
            UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true,
            RedirectStandardError = true, StandardOutputEncoding = Encoding.UTF8, StandardErrorEncoding = Encoding.UTF8
        };
        foreach (var arg in new[] { "-s", serial, "logcat", "-b", "all", "-v", "threadtime", "-T", "1" })
            info.ArgumentList.Add(arg);
        process = new() { StartInfo = info };
        try {
            writer.WriteLine($"# Voyah Iran logcat; start={DateTimeOffset.Now:O}; device={serial}");
            if (!process.Start()) throw new IOException("Could not start logcat.");
            Completion = ObserveAsync(PumpAsync(process.StandardOutput, false), PumpAsync(process.StandardError, true));
        }
        catch { writer.Dispose(); process.Dispose(); throw; }
    }
    public static async Task<LogcatCapture> StartAsync(string executable, string serial, string filePath, IAdbClient adb)
    {
        var marker = "VOYAH_LOG_START_" + Guid.NewGuid().ToString("N");
        var capture = new LogcatCapture(executable, serial, filePath, marker);
        try {
            await capture.readerReady.Task.WaitAsync(TimeSpan.FromSeconds(10));
            // No clearing of the vehicle's existing logs; the marker establishes our boundary.
            var result = await adb.RunAsync(serial, ["shell", "log", "-p", "i", "-t", "VoyahIranCapture", marker], 10);
            if (result.ExitCode != 0) throw new IOException("ارسال نشان شروع گزارش‌گیری ناموفق بود: " + result.Output);
            await capture.started.Task.WaitAsync(TimeSpan.FromSeconds(10));
            return capture;
        }
        catch {
            await capture.StopAsync();
            capture.Dispose();
            throw;
        }
    }
    private async Task PumpAsync(StreamReader reader, bool error)
    {
        try {
            while (await reader.ReadLineAsync().ConfigureAwait(false) is { } line) {
                if (!error) readerReady.TrySetResult();
                lock (gate) {
                    if (error) writer.WriteLine("# adb: " + line);
                    else if (boundary.Accept(line)) {
                        writer.WriteLine(line);
                        started.TrySetResult();
                    }
                }
            }
        }
        catch (Exception e) {
            pumpError = e.Message;
            Kill();
        }
    }
    private async Task<LogcatCompletion> ObserveAsync(Task stdout, Task stderr)
    {
        try {
            await process.WaitForExitAsync().ConfigureAwait(false);
            await Task.WhenAll(stdout, stderr).ConfigureAwait(false);
            var result = new LogcatCompletion(process.ExitCode, stopping, pumpError);
            lock (gate) {
                writer.WriteLine($"# End={DateTimeOffset.Now:O}; exit={result.ExitCode}; requestedStop={stopping}; error={pumpError}");
                writer.Dispose();
            }
            return result;
        }
        catch (Exception e) {
            lock (gate) writer.Dispose();
            return new(-1, stopping, e.Message);
        }
        finally {
            var error = new IOException("ارتباط گزارش‌گیری پیش از دریافت نشان شروع بسته شد.");
            readerReady.TrySetException(error); started.TrySetException(error);
        }
    }
    private void Kill()
    {
        try { if (!process.HasExited) process.Kill(entireProcessTree: true); }
        catch (InvalidOperationException) { }
    }
    public async Task<LogcatCompletion> StopAsync()
    {
        stopping = true;
        Kill();
        return await Completion.ConfigureAwait(false);
    }
    public void Dispose()
    {
        if (!Completion.IsCompleted) { stopping = true; Kill(); }
        // ObserveAsync owns the writer; don't close it before the stdout/stderr pipes drain.
        if (Completion.IsCompleted) process.Dispose();
        else _ = Completion.ContinueWith(_ => process.Dispose(), TaskScheduler.Default);
    }
}
