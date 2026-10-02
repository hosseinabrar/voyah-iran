using VoyahIran.Installer.Core;
internal static class LogcatCaptureTests
{
    public static async Task Run(string root)
    {
        var executable = Path.Combine(AppContext.BaseDirectory, "VoyahIran.Installer.Tests.exe");
        var marker = Path.Combine(root, "marker with spaces.txt");
        var file = Path.Combine(root, "capture.txt");
        using (var capture = await LogcatCapture.StartAsync(executable, marker, file, new MarkerAdb())) {
            var deadline = DateTime.UtcNow.AddSeconds(5);
            while (!ReadShared(file).Contains("گزارش تازه خودرو") && DateTime.UtcNow < deadline) await Task.Delay(20);
            var result = await capture.StopAsync();
            var output = File.ReadAllText(file);
            Require(result.RequestedStop, "user stop recorded");
            Require(!output.Contains("old buffered log"), "old backlog excluded");
            Require(output.Contains("گزارش تازه خودرو"), "UTF8 streamed to disk");
            Require(output.Contains("diagnostic stderr"), "stderr drained on stop");
            Require(output.Contains("# End="), "output finalized before save");
        }
        marker = Path.Combine(root, "disconnect.txt");
        file = Path.Combine(root, "partial.txt");
        File.WriteAllText(marker + ".exit", "");
        using (var capture = await LogcatCapture.StartAsync(executable, marker, file, new MarkerAdb())) {
            var result = await capture.Completion.WaitAsync(TimeSpan.FromSeconds(5));
            Require(!result.RequestedStop, "disconnect detected");
            Require(File.ReadAllText(file).Contains("گزارش تازه خودرو"), "partial log retained");
        }
        var failed = false;
        try {
            using var unexpected = await LogcatCapture.StartAsync(executable,
                Path.Combine(root, "rejected-marker.txt"), Path.Combine(root, "rejected.txt"), new MarkerAdb(true));
        } catch (IOException) { failed = true; }
        Require(failed, "marker rejection cannot report successful capture");
        failed = false;
        var stoppedFile = Path.Combine(root, "stopped-logd.txt");
        try {
            using var unexpected = await LogcatCapture.StartAsync(executable, "unused", stoppedFile, new MarkerAdb(stopped: true));
        } catch (IOException e) { failed = e.Message.Contains("افزایش سرعت نمایشگرها"); }
        Require(failed && !File.Exists(stoppedFile), "stopped logging explains recovery without creating an empty capture");
        Console.WriteLine("PASS: logcat capture lifecycle, disconnect and startup failure");
    }
    private static string ReadShared(string path) {
        using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
        using var reader = new StreamReader(stream);
        return reader.ReadToEnd();
    }
    private static void Require(bool value, string name) {
        if (!value) throw new Exception("FAIL logcat: " + name);
    }
    private sealed class MarkerAdb(bool reject = false, bool stopped = false) : IAdbClient {
        public Task<CommandResult> RunAsync(string? serial, IReadOnlyList<string> args, int timeoutSeconds = 30, bool quiet = false) {
            if (args.SequenceEqual(new[] { "shell", "getprop", "init.svc.logd" }))
                return Task.FromResult(new CommandResult(0, stopped ? "stopped" : "running"));
            if (reject) return Task.FromResult(new CommandResult(1, "simulated log command failure"));
            File.WriteAllText(serial! + ".tmp", args[^1]);
            File.Move(serial! + ".tmp", serial!);
            return Task.FromResult(new CommandResult(0, ""));
        }
    }
}
