using System.Diagnostics;
using System.Text;
using System.Text.RegularExpressions;

namespace VoyahIran.Installer.Core;

public record CommandResult(int ExitCode, string Output);
public interface IAdbClient
{
    Task<CommandResult> RunAsync(string? serial, IReadOnlyList<string> args, int timeoutSeconds = 30, bool quiet = false);
}
public sealed class AdbClient(string executable, SessionLog log) : IAdbClient
{
    public async Task<CommandResult> RunAsync(string? serial, IReadOnlyList<string> args, int timeoutSeconds = 30, bool quiet = false)
    {
        var info = new ProcessStartInfo(executable) { UseShellExecute = false, CreateNoWindow = true,
            RedirectStandardOutput = true, RedirectStandardError = true, StandardOutputEncoding = Encoding.UTF8, StandardErrorEncoding = Encoding.UTF8 };
        if (serial != null) { info.ArgumentList.Add("-s"); info.ArgumentList.Add(serial); }
        foreach (var arg in args) info.ArgumentList.Add(arg);
        var id = Guid.NewGuid().ToString("N")[..8];
        if (!quiet) log.Write($"[{id}] adb {string.Join(" ", info.ArgumentList.Select(a => '\"' + a + '\"'))}");
        using var process = new Process { StartInfo = info };
        var output = new StringBuilder();
        var gate = new object();
        void Receive(string? line, string stream)
        {
            if (line == null) return;
            lock (gate) output.AppendLine(line);
            if (!quiet) log.Write($"[{id}/{stream}] {line}");
        }
        process.OutputDataReceived += (_, e) => Receive(e.Data, "stdout");
        process.ErrorDataReceived += (_, e) => Receive(e.Data, "stderr");
        process.Start(); process.BeginOutputReadLine(); process.BeginErrorReadLine();
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(timeoutSeconds));
        try { await process.WaitForExitAsync(timeout.Token); process.WaitForExit(); }
        catch (OperationCanceledException)
        {
            try { process.Kill(true); } catch (Exception e) { log.Write($"[{id}] Could not stop local ADB process: {e.Message}"); }
            log.Write($"[{id}] TIMEOUT after {timeoutSeconds}s. Remote operation may still be running; no retry or reboot performed.");
            throw new TimeoutException("مهلت پاسخ خودرو تمام شد؛ عملیات ممکن است روی خودرو ادامه داشته باشد. لاگ را برای پشتیبانی بفرستید.");
        }
        if (!quiet) log.Write($"[{id}] exit={process.ExitCode}");
        return new(process.ExitCode, output.ToString());
    }
}

public record Device(string Serial, string State);
public static class DeviceParser
{
    public static IReadOnlyList<Device> Parse(string output) => output.Split('\n')
        .Select(line => Regex.Match(line.Trim(), @"^(\S+)\s+(device|unauthorized|offline|recovery|sideload|no permissions)(?:\s|$)"))
        .Where(m => m.Success).Select(m => new Device(m.Groups[1].Value, m.Groups[2].Value)).ToArray();
    public static Device? SingleReady(IReadOnlyList<Device> devices) => devices.Count == 1 && devices[0].State == "device" ? devices[0] : null;
    public static string Describe(IReadOnlyList<Device> devices) => devices.Count switch
    {
        0 => "خودرو شناسایی نشده",
        > 1 => "بیش از یک دستگاه متصل است؛ فقط خودرو را متصل نگه دارید",
        _ => devices[0].State switch { "device" => "خودرو شناسایی شد", "unauthorized" => "اجازهٔ اتصال را روی نمایشگر خودرو تأیید کنید", "offline" => "خودرو متصل است، اما پاسخ نمی‌دهد", _ => "اتصال خودرو آمادهٔ نصب نیست" }
    };
}
