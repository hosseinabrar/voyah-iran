using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;

namespace VoyahIran.Installer.Core;

/// Replaying the same credential is idempotent. Never replay APK/framework installation.
public sealed class BridgeCredentialProvisioner(IAdbClient adb, Action<string> log, Func<Task>? pause = null)
{
    public async Task Provision(string serial, string token)
    {
        if(!Regex.IsMatch(token,@"\A[a-f0-9]{64}\z"))throw new ArgumentException("Invalid bridge credential");
        var expected=Convert.ToHexString(SHA256.HashData(Encoding.ASCII.GetBytes(token))).ToLowerInvariant();
        async Task<bool> Verified()
        {
            try {
                var r=await adb.RunAsync(serial,["shell","sha256sum","/data/user/0/com.abrar.voyahiran/files/bridge.token"],5,true);
                return r.ExitCode==0 && r.Output.Split((char[]?)null,StringSplitOptions.RemoveEmptyEntries).FirstOrDefault()==expected;
            } catch(TimeoutException) { return false; }
        }
        async Task Pause() { if(pause!=null)await pause();else await Task.Delay(500); }
        if(await Verified()){log("App bridge credential already verified.");return;}
        for(var attempt=1;attempt<=3;attempt++) {
            log($"Provisioning app bridge credential, attempt {attempt}/3.");
            try {
                var response=await adb.RunAsync(serial,["shell","/system/bin/am","broadcast","--user","0",
                    "--include-stopped-packages","--receiver-foreground","-n","com.abrar.voyahiran/.core.bridge.BridgeProvisionReceiver",
                    "--es","token",token],10);
                if(response.ExitCode!=0 || !Regex.IsMatch(response.Output,@"\bresult=200\b"))
                    log("Provision acknowledgment unavailable; checking persisted credential before retry.");
            } catch(TimeoutException) {
                log("Provision acknowledgment timed out; checking persisted credential before retrying only the same credential.");
            }
            for(var check=0;check<5;check++) {
                if(await Verified()){log("App bridge credential persisted and verified.");return;}
                await Pause();
            }
        }
        throw new IOException("تحویل کلید اتصال واسط تأیید نشد؛ دوباره تلاش کنید. فایل برنامه و واسط نصب شده‌اند.");
    }
}
