using VoyahIran.Installer.Core;
static class VoyahPermissionsTests
{
    public static async Task Run()
    {
        foreach(var (output,expected) in new (string,bool)[]{
            ("FINE_LOCATION: allow",true),
            ("No operations.\nDefault mode: allow\n",true),
            ("No operations.\r\nDefault mode: allow\r\n",true),
            ("No operations.",false),
            ("No operations.\nDefault mode: ignore",false),
            ("FINE_LOCATION: ignore\nDefault mode: allow",false),
            ("COARSE_LOCATION: allow",false),
            ("Uid mode: FINE_LOCATION: allow\nFINE_LOCATION: ignore",false),
            ("FINE_LOCATION: foreground",false)})
            if(VoyahPermissions.IsAppOpAllowed(output,"FINE_LOCATION")!=expected)
                throw new Exception("AppOps response parsing: "+output);
        foreach(var sdk in new[]{28,30}) foreach(var fail in new[]{"none","grant","verify","op","unsupported","flags-error","unsupported-grant","unsupported-verify","default-allow"})
        {
            var adb=new Fake(sdk,fail);var failed=false;
            try {await VoyahPermissions.GrantAsync(adb,"CAR",_=>{});}catch(IOException){failed=true;}
            if(failed!=(fail is not ("none" or "unsupported" or "default-allow")))throw new Exception("Permissions failure handling "+fail);
            if(fail is "none" or "unsupported" or "default-allow") {
                if(!adb.Calls.Any(a=>a.Contains("REQUEST_INSTALL_PACKAGES")))throw new Exception("Missing install permission");
                if(adb.Calls.Any(a=>a.Contains("MANAGE_EXTERNAL_STORAGE"))!=(sdk>=30))throw new Exception("Wrong storage API");
                if(adb.Calls.Any(a=>a.Contains("android.permission.WRITE_EXTERNAL_STORAGE"))!=(sdk==28))throw new Exception("Wrong legacy storage grant");
                if(adb.Calls.Count(a=>a.Contains("clear-permission-flags"))!=(fail=="unsupported"?1:adb.Granted.Count))throw new Exception("Denied permission recovery missing");
            }
            Console.WriteLine($"PASS: Voyah permissions SDK {sdk} {fail}");
        }
    }
    sealed class Fake(int sdk,string fail):IAdbClient
    {
        public HashSet<string> Granted=[];
        public List<IReadOnlyList<string>> Calls=[];
        private bool root;
        public Task<CommandResult> RunAsync(string? serial,IReadOnlyList<string> a,int timeoutSeconds=30,bool quiet=false)
        {
            if(serial!="CAR")throw new Exception("Wrong device");Calls.Add(a);var code=0;var text="";
            if(a[0]=="root")root=true;
            if(a.Contains("id"))text=root?"0":"2000";
            if(a.Contains("path"))text="package:/data/app/base.apk";
            if(a.Contains("ro.build.version.sdk"))text=sdk.ToString();
            if(a.Contains("clear-permission-flags")) {
                if(fail.StartsWith("unsupported")){code=255;text="Unknown command: clear-permission-flags\n";}
                if(fail=="flags-error"){code=1;text="SecurityException: permission denied";}
            }
            if(a.Contains("grant")){Granted.Add(a.Last());if(fail.EndsWith("grant"))code=1;}
            if(a.Contains("dumpsys"))text="User 0: installed=true\\n".Replace("\\n","\n")+string.Join("\n",Granted.Select(p=>p+": granted="+(fail.EndsWith("verify")?"false":"true")));
            if(a.Contains("appops")&&a.Contains("get"))text=fail=="default-allow"?"No operations.\nDefault mode: allow":a.Last()+": "+(fail=="op"?"ignore":"allow");
            return Task.FromResult(new CommandResult(code,text));
        }
    }
}
