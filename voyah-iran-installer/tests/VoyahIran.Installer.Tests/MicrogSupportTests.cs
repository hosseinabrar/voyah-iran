using VoyahIran.Installer.Core;
static class MicrogSupportTests
{
    sealed class Adb(PayloadBundle bundle,string mode,int sdk) : IAdbClient
    {
        public readonly List<string[]> Calls=[];
        public Task<CommandResult> RunAsync(string? serial,IReadOnlyList<string> arguments,int timeoutSeconds=30,bool quiet=false)
        {
            if(serial!="CAR")throw new Exception("Wrong device");
            var a=arguments.ToArray();Calls.Add(a);string value="";
            if(a.Contains("sha256sum"))value=mode=="hash-error" ? "bad" : bundle.Manifest.Files["patch/microg/provision.dex"]+"  file";
            else if(a.Any(x=>x.Contains("app_process /system/bin MicrogProvision")))value=mode switch {
                "absent"=>"MICROG_ABSENT", "nonofficial"=>"MICROG_NON_OFFICIAL_SKIPPED", "missing-marker"=>"", _=>"MICROG_OFFICIAL_READY"};
            else if(a.Contains("getprop"))value=sdk.ToString();
            else if(a.Contains("list"))value=mode=="companion-present"?"package:com.android.vending":"";
            else if(a[0]=="install")value="Success";
            else if(a.Contains("dumpsys"))value="User 0:\n android.permission.ACCESS_COARSE_LOCATION: granted=true\n android.permission.ACCESS_FINE_LOCATION: granted=true\n android.permission.ACCESS_BACKGROUND_LOCATION: granted=true\n";
            return Task.FromResult(new CommandResult(0,value));
        }
    }
    public static async Task Run(PayloadBundle bundle,Action<bool,string> check)
    {
        foreach(var runtime in new[]{"win-x64","osx-x64","osx-arm64"})
            check(MicrogSupport.Assets.All(PayloadBundle.RequiredFiles(runtime).Contains),"microG assets included: "+runtime);
        foreach(var mode in new[]{"absent","nonofficial","present","companion-present","hash-error","missing-marker"})
        {
            var adb=new Adb(bundle,mode,30);bool failed=false;
            try {await MicrogSupport.PrepareAsync(adb,"CAR",bundle,_=>{});}catch(IOException){failed=true;}
            check(failed==(mode is "hash-error" or "missing-marker"),"microG preparation outcome: "+mode);
            check(!adb.Calls.Any(a=>a[0]=="install" || a.Contains("grant")),"microG preserves existing services: "+mode);
        }
        var legacy=new Adb(bundle,"present",28);await MicrogSupport.PrepareAsync(legacy,"CAR",bundle,_=>{});
        check(!legacy.Calls.Any(a=>a.Contains("android.permission.ACCESS_BACKGROUND_LOCATION")),"Android 9 omits unavailable background location permission");
    }
}
