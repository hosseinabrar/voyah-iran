using VoyahIran.Installer.Core;

static class PermissionSelectionTests
{
    public static async Task Run()
    {
        var fake=new Adb();
        using var log=new SessionLog(Path.Combine(Path.GetTempPath(),"permission-selection-"+Guid.NewGuid()+".log"));
        var installer=new ApkInstaller(fake,log,"CAR");
        var info=new ApkInfo("com.example.app",new());
        var choices=ApkInstaller.Choices.Where(c=>c.Names.Contains("RECORD_AUDIO") || c.Names.Contains("REQUEST_INSTALL_PACKAGES")).ToArray();
        try { await installer.SetAsync(info,choices,true);throw new Exception("Undeclared runtime grant was reported successful"); }
        catch(AggregateException) { }
        if(!fake.InstallAllowed)throw new Exception("Unsupported runtime permission blocked the independent install permission");
        if(await installer.ReadAsync(info,choices[0]))throw new Exception("Denied microphone grant appears enabled");
    }
    private sealed class Adb:IAdbClient {
        public bool InstallAllowed;
        public Task<CommandResult> RunAsync(string? serial,IReadOnlyList<string> args,int timeoutSeconds=30,bool quiet=false){
            if(args.Contains("package"))return Task.FromResult(new CommandResult(0,"  User 0: installed=true\n"));
            if(args.Contains("pm"))return Task.FromResult(args.Contains("grant") ? new CommandResult(1,"SecurityException: permission was not requested") : new CommandResult(0,""));
            if(args.Contains("REQUEST_INSTALL_PACKAGES")) {
                if(args.Contains("set"))InstallAllowed=args.Last()=="allow";
                return Task.FromResult(new CommandResult(0,"REQUEST_INSTALL_PACKAGES: "+(InstallAllowed?"allow":"default")));
            }
            throw new Exception("Unexpected command");
        }
    }
}
