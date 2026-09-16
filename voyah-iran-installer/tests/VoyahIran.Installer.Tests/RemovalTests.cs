using VoyahIran.Installer.Core;
static class RemovalTests
{
    public static async Task Run(SessionLog log)
    {
        foreach (var pkg in new[] { "com.abrar.voyahiran", "com.test;reboot", "com.test\n" })
        {
            var adb = new Fake();
            try { await new AppRemoval(adb, log, "test", "unused").RemoveAsync(pkg); throw new Exception("Unsafe removal accepted"); }
            catch (InvalidOperationException) { if (adb.Calls.Count != 0) throw new Exception("Unsafe package reached ADB"); }
        }
        var missing = new Fake(new CommandResult(0, "package:com.other\n"));
        try { await new AppRemoval(missing, log, "test", "unused").RemoveAsync("com.test"); throw new Exception("Unlisted app accepted"); }
        catch (InvalidOperationException) { if (missing.Calls.Count != 1) throw new Exception("Unexpected deletion"); }
        var valid = new Fake(new CommandResult(0, "package:com.test\n"), new CommandResult(0, "Success\n"), new CommandResult(0, ""));
        await new AppRemoval(valid, log, "test", "unused").RemoveAsync("com.test");
        if (string.Join(" ", valid.Calls[1]) != "uninstall --user 0 com.test") throw new Exception("Wrong delete scope");
        var failed = new Fake(new CommandResult(0, "package:com.test\n"), new CommandResult(0, "Failure [DELETE_FAILED]\n"));
        try { await new AppRemoval(failed, log, "test", "unused").RemoveAsync("com.test"); throw new Exception("Failure treated as success"); }
        catch (IOException) { }
        Console.WriteLine("PASS: uninstall exclusion, input validation, user scope and failed removal");
    }
    private sealed class Fake(params CommandResult[] results) : IAdbClient
    {
        public List<string[]> Calls { get; } = [];
        public Task<CommandResult> RunAsync(string? serial, IReadOnlyList<string> args, int timeoutSeconds = 30, bool quiet = false)
        {
            Calls.Add(args.ToArray()); return Task.FromResult(results[Calls.Count - 1]);
        }
    }
}
