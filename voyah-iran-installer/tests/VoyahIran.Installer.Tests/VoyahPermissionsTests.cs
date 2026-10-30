using VoyahIran.Installer.Core;
static class VoyahPermissionsTests
{
    public static async Task Run()
    {
        foreach(var sdk in new[]{28,30}) foreach(var fail in new[]{"none","grant","verify","op"})
        {
            var adb=new Fake(sdk,fail);var failed=false;
            try {await VoyahPermissions.GrantAsync(adb,"CAR",_=>{});}catch(IOException){failed=true;}
            if(failed!=(fail!="none"))throw new Exception("Permissions failure handling "+fail);
            if(fail=="none") {
                if(!adb.Calls.Any(a=>a.Contains("REQUEST_INSTALL_PACKAGES")))throw new Exception("Missing install permission");
                if(adb.Calls.Any(a=>a.Contains("MANAGE_EXTERNAL_STORAGE"))!=(sdk>=30))throw new Exception("Wrong storage API");
                if(adb.Calls.Any(a=>a.Contains("android.permission.WRITE_EXTERNAL_STORAGE"))!=(sdk==28))throw new Exception("Wrong legacy storage grant");
                if(adb.Calls.Count(a=>a.Contains("clear-permission-flags"))!=adb.Granted.Count)throw new Exception("Denied permission recovery missing");
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
            if(a.Contains("grant")){Granted.Add(a.Last());if(fail=="grant")code=1;}
            if(a.Contains("dumpsys"))text="User 0: installed=true\\n".Replace("\\n","\n")+string.Join("\n",Granted.Select(p=>p+": granted="+(fail=="verify"?"false":"true")));
            if(a.Contains("appops")&&a.Contains("get"))text=a.Last()+": "+(fail=="op"?"ignore":"allow");
            return Task.FromResult(new CommandResult(code,text));
        }
    }
}
