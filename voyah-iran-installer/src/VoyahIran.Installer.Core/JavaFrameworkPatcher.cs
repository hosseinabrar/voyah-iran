using System.Diagnostics;
using System.Text;
using System.Text.RegularExpressions;

namespace VoyahIran.Installer.Core;

public record FrameworkPatchResult(string OriginalHash, string PatchedHash, string FilePath, bool AlreadyPatched);
public interface IFrameworkPatcher
{
    Task<FrameworkPatchResult> PatchAsync(string input, string workDirectory);
}
public sealed class JavaFrameworkPatcher(PayloadBundle bundle, SessionLog log) : IFrameworkPatcher
{
    public async Task<FrameworkPatchResult> PatchAsync(string input, string workDirectory)
    {
        var info = new ProcessStartInfo(bundle.File("java/bin/java.exe")) {
            UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true,
            RedirectStandardError = true, StandardOutputEncoding = Encoding.UTF8, StandardErrorEncoding = Encoding.UTF8
        };
        foreach (var arg in new[] { "-Xmx768m", bundle.File("patch/FrameworkPatch.java"), bundle.File("patch/apktool.jar"),
            bundle.File("patch"), input, workDirectory }) info.ArgumentList.Add(arg);
        log.Write("LOCAL PATCH: using bundled Java and APKTool; no ADB writes during preparation.");
        using var process = new Process { StartInfo = info };
        var verified = false;
        process.OutputDataReceived += (_, e) => {
            if (e.Data == null) return;
            if (e.Data.StartsWith("VOYAH_PATCH_VERIFIED ", StringComparison.Ordinal)) verified = true;
            log.Write("[patch] " + e.Data);
        };
        process.ErrorDataReceived += (_, e) => { if (e.Data != null) log.Write("[patch/stderr] " + e.Data); };
        process.Start(); process.BeginOutputReadLine(); process.BeginErrorReadLine();
        using var timeout = new CancellationTokenSource(TimeSpan.FromMinutes(15));
        try { await process.WaitForExitAsync(timeout.Token); process.WaitForExit(); }
        catch (OperationCanceledException) {
            try { process.Kill(true); } catch (Exception e) { log.Write("Local patch shutdown: " + e.Message); }
            throw new TimeoutException("ساخت پچ روی لپ‌تاپ به پایان نرسید. فایل سیستم خودرو تغییر نکرده است.");
        }
        if (process.ExitCode != 0 || !verified) throw new InvalidDataException("ساخت یا بررسی پچ کیبورد ناموفق بود. خودرو تغییر نکرده است؛ گزارش را برای پشتیبانی بفرستید.");
        var values = System.IO.File.ReadAllLines(Path.Combine(workDirectory, "result.txt"))
            .Select(l => l.Split('=', 2)).ToDictionary(p => p[0], p => p[1], StringComparer.Ordinal);
        if (values.Count != 3 || !Regex.IsMatch(values["original"], "^[0-9a-f]{64}$") || !Regex.IsMatch(values["patched"], "^[0-9a-f]{64}$") || !bool.TryParse(values["already"], out var already))
            throw new InvalidDataException("Invalid patch result");
        var output = Path.Combine(workDirectory, "patched.jar");
        if (PayloadBundle.Digest(input) != values["original"] || PayloadBundle.Digest(output) != values["patched"])
            throw new InvalidDataException("Local patch integrity failed");
        log.Write($"LOCAL PATCH VERIFIED: original={values["original"]}; patched={values["patched"]}; already={already}");
        return new(values["original"], values["patched"], output, already);
    }
}
