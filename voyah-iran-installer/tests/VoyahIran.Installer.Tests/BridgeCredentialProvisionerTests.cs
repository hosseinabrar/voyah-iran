using System.Security.Cryptography;
using System.Text;
using VoyahIran.Installer.Core;

static class BridgeCredentialProvisionerTests {
    public static async Task Run() {
        foreach(var mode in new[]{"normal","timeout-delivered","retry","missing","already"}) {
            var adb=new Fake(mode);
            var failed=false;
            try {await new BridgeCredentialProvisioner(adb,_=>{},()=>Task.CompletedTask).Provision("CAR",new string('a',64));}
            catch(IOException){failed=true;}
            if(failed!=(mode=="missing"))throw new Exception("Wrong provision result: "+mode);
            var expected=mode switch {"already"=>0,"retry"=>2,"missing"=>3,_=>1};
            if(adb.Broadcasts!=expected)throw new Exception("Wrong provision retry count: "+mode);
        }
    }
    private sealed class Fake(string mode):IAdbClient {
        public int Broadcasts;
        private bool delivered=mode=="already";
        public Task<CommandResult> RunAsync(string? serial,IReadOnlyList<string> args,int timeoutSeconds=30,bool quiet=false) {
            if(serial!="CAR")throw new Exception("Wrong device");
            if(args.Contains("sha256sum"))return Task.FromResult(new CommandResult(delivered?0:1,delivered ? Convert.ToHexString(SHA256.HashData(Encoding.ASCII.GetBytes(new string('a',64)))).ToLowerInvariant()+"  file" : "missing"));
            if(!args.Contains("broadcast") || !args.Contains("--receiver-foreground"))throw new Exception("Unexpected provision operation");
            Broadcasts++;
            delivered=mode=="normal" || mode=="timeout-delivered" || mode=="retry" && Broadcasts==2;
            if(mode=="timeout-delivered" || mode=="retry" && Broadcasts==1)throw new TimeoutException();
            return Task.FromResult(new CommandResult(0,"Broadcast completed: result=200"));
        }
    }
}
