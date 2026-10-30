using VoyahIran.Installer.Core;

static class ApkInstallRecoveryTests
{
    public static async Task Run()
    {
        foreach(var mode in new[]{"committed","old","absent","repeat","bad-hash","permanent","timeout"})
        {
            var fake=new RecoveryAdb(mode);var prepares=0;var failed=false;
            CommandResult? result=null;
            try { result=await new ApkInstallRecovery(fake,_=>{},()=>Task.CompletedTask).Run("CAR","--no-streaming","test.apk",new string('a',64),()=>{prepares++;return Task.CompletedTask;}); }
            catch(Exception e) when(e is IOException or TimeoutException) {failed=true;}
            void Check(bool ok,string reason){if(!ok)throw new Exception(mode+": "+reason);}
            Check(fake.Serials.All(s=>s=="CAR"),"must reconnect only to selected serial");
            if(mode=="committed")Check(!failed && result!.ExitCode==0 && fake.Installs==1 && prepares==1,"accept verified committed APK without reinstallation");
            if(mode is "old" or "absent")Check(!failed && result!.ExitCode==0 && fake.Installs==2,"retry once after recovery");
            if(mode=="repeat")Check(!failed && result!.ExitCode!=0 && fake.Installs==2,"bounded attempts");
            if(mode=="bad-hash")Check(failed && fake.Installs==1,"unknown hash must not retry");
            if(mode=="permanent")Check(!failed && result!.ExitCode!=0 && prepares==0 && fake.Installs==1,"permanent error unchanged");
            if(mode=="timeout")Check(failed && prepares==0 && fake.Installs==1,"ambiguous timeout not retried");
            if(prepares>0)Check(fake.Polls>=3,"wait through missing service and require two stable probes");
            Console.WriteLine("PASS: APK recovery "+mode);
        }
    }
    sealed class RecoveryAdb(string mode):IAdbClient
    {
        public int Installs,Polls;
        public List<string?> Serials=[];
        public Task<CommandResult> RunAsync(string? serial,IReadOnlyList<string> args,int timeoutSeconds=30,bool quiet=false)
        {
            Serials.Add(serial);var code=0;var text="";
            if(args[0]=="install") {
                Installs++;
                if(mode=="timeout")throw new TimeoutException();
                code=1;text= mode=="permanent"?"INSTALL_FAILED_UPDATE_INCOMPATIBLE":"cmd: Failure calling service package: Broken pipe (32)";
                if(Installs==2 && mode is "old" or "absent"){code=0;text="Success";}
            }
            else if(args.Contains("sys.boot_completed")){Polls++;text="1";}
            else if(args.Contains("pidof"))text="123";
            else if(args.Contains("device_provisioned")){code=Polls==1?1:0;text="1";}
            else if(args.Contains("/proc/sys/kernel/random/boot_id"))text="00000000-0000-0000-0000-000000000001";
            else if(args.Contains("path")) {text="package:/data/app/base.apk";if(mode=="absent" && args.Contains(InstallAppGuard.Package)){code=1;text="";}}
            else if(args.Contains("sha256sum"))text=mode=="bad-hash"?"bad":new string(mode=="committed"?'a':'b',64)+" /data/app/base.apk";
            return Task.FromResult(new CommandResult(code,text));
        }
    }
}
