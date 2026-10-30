using VoyahIran.Installer.Core;
static class FrameworkServiceRecoveryTests
{
    public static async Task Run()
    {
        foreach(var mode in new[]{"recover","persistent","denied","other","package"})
        {
            var adb=new Fake(mode);var waits=0;
            var result=await new FrameworkServiceRecovery(adb,_=>{},()=>{waits++;return Task.CompletedTask;})
                .Run("CAR",["shell",mode=="other"?"am":mode=="package"?"pm":"/system/bin/settings","put","global","voyah_iran_launcher_lease","0"],15);
            var retry=mode is "recover" or "persistent" or "package";
            if(adb.Calls!=(retry?2:1) || waits!=(retry?1:0) || (result.ExitCode==0)!=(mode is "recover" or "package"))
                throw new Exception("Settings service recovery: "+mode);
            Console.WriteLine("PASS: Settings service recovery "+mode);
        }
    }
    sealed class Fake(string mode):IAdbClient
    {
        public int Calls;
        public Task<CommandResult> RunAsync(string? serial,IReadOnlyList<string> args,int timeoutSeconds=30,bool quiet=false)
        {
            if(serial!="CAR")throw new Exception("Wrong device");
            Calls++;
            return Task.FromResult((mode is "recover" or "package")&&Calls==2?new CommandResult(0,""):
                new CommandResult(20,mode=="denied"?"Permission denied":"cmd: Can't find service: "+(mode=="package"?"package":"settings")+"\n"));
        }
    }
}
