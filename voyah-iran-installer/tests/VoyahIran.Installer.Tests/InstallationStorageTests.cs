using VoyahIran.Installer.Core;

internal static class InstallationStorageTests
{
    public static async Task Run(string root)
    {
        var test=Path.Combine(root,"storage");
        var backups=Path.Combine(test,"Backups");
        string Create(string parent,string name) {var p=Path.Combine(parent,name);Directory.CreateDirectory(p);File.WriteAllText(Path.Combine(p,"fixture"),"data");return p;}
        var old=Create(backups,"20261016-123000-"+Guid.NewGuid().ToString("N"));
        var active=Create(backups,"20261016-123001-"+Guid.NewGuid().ToString("N"));
        var unknown=Create(backups,"user-backup");
        var cache=Path.Combine(test,"Cache");
        var current=Create(cache,Guid.NewGuid().ToString("N"));
        var prior=Create(cache,Guid.NewGuid().ToString("N"));
        var busy=Create(cache,Guid.NewGuid().ToString("N"));
        using(var lock1=new FileStream(Path.Combine(active,".installer-lease"),FileMode.Create,FileAccess.ReadWrite,FileShare.None))
        using(var lock2=new FileStream(Path.Combine(busy,".installer-lease"),FileMode.Create,FileAccess.ReadWrite,FileShare.None)) {
            InstallationStorage.CleanLocal(backups,current,Console.WriteLine);
            Check(!Directory.Exists(old)&&!Directory.Exists(prior),"obsolete backups/cache removed");
            Check(Directory.Exists(active)&&Directory.Exists(busy),"active installations retained");
            Check(Directory.Exists(unknown)&&Directory.Exists(current),"unknown and current payload retained");
        }
        InstallationStorage.CleanPayload(current,Console.WriteLine);
        Check(!Directory.Exists(current),"current payload removed at exit");
        InstallationStorage.CleanPayload(unknown,Console.WriteLine);
        Check(Directory.Exists(unknown),"cleanup cannot target arbitrary directory");
        if(!OperatingSystem.IsWindows()) {
            var remote=Path.Combine(test,"remote");Directory.CreateDirectory(remote);
            var obsolete=Create(remote,"voyahiran-backup-1792139028");Directory.CreateDirectory(Path.Combine(obsolete,"bridge"));
            var live=Create(remote,"voyahiran-bridge");
            var original=Create(remote,"voyahiran-framework-backups");
            var foreign=Create(remote,"voyahiran-backup-not-a-timestamp");
            var outside=Create(test,"outside");
            Directory.CreateSymbolicLink(Path.Combine(remote,"voyahiran-backup-1792139029"),outside);
            var nested=Create(remote,"voyahiran-backup-1792139030");Directory.CreateSymbolicLink(Path.Combine(nested,"bridge"),outside);
            var script=InstallationStorage.RemoteCleanup.Replace("/data/local/tmp",remote)
                .Replace("[ \"$(id -u)\" = 0 ] || exit 1","true");
            var info=new System.Diagnostics.ProcessStartInfo("/bin/sh") {RedirectStandardOutput=true,RedirectStandardError=true};
            info.ArgumentList.Add("-c");info.ArgumentList.Add(script);
            using var process=System.Diagnostics.Process.Start(info)!;
            var output=await process.StandardOutput.ReadToEndAsync();var errors=await process.StandardError.ReadToEndAsync();
            await process.WaitForExitAsync();Check(process.ExitCode==0,errors);
            Check(!Directory.Exists(obsolete),"legacy bridge backup removed");
            Check(Directory.Exists(live)&&Directory.Exists(original)&&Directory.Exists(foreign),"live bridge, firmware originals and unknown names retained");
            Check(File.Exists(Path.Combine(outside,"fixture"))&&Directory.Exists(nested),"symlinks never traversed");
            // Remove only fixture links so the outer test-directory cleanup stays inside its sandbox.
            Directory.Delete(Path.Combine(remote,"voyahiran-backup-1792139029"));Directory.Delete(Path.Combine(nested,"bridge"));
        }
    }
    private static void Check(bool ok,string message) {if(!ok)throw new Exception(message);Console.WriteLine("PASS storage: "+message);}
}
