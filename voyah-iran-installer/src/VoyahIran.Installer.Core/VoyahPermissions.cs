using System.Text.RegularExpressions;

namespace VoyahIran.Installer.Core;

public static class VoyahPermissions
{
    public static bool IsAppOpAllowed(string output, string op)
    {
        // An unset operation can be allowed by its default. Android prints this
        // instead of an explicit per-package entry (e.g. FINE_LOCATION).
        var explicitMode=Regex.Match(output,@"(?m)^[ \t]*"+Regex.Escape(op)+@":[ \t]*(\w+)\b");
        if(explicitMode.Success) return explicitMode.Groups[1].Value=="allow";
        return Regex.IsMatch(output,@"\A\s*No operations\.\s*Default mode:[ \t]*allow\s*\z");
    }

    public static async Task GrantAsync(IAdbClient adb, string serial, Action<string> log)
    {
        async Task<string> Run(params string[] args)
        {
            var r=await new FrameworkServiceRecovery(adb,log).Run(serial,args,30);
            if(r.ExitCode!=0 || Regex.IsMatch(r.Output,@"(?i)\b(exception|error:|unknown operation)"))
                throw new IOException("اعطای دسترسی کامل نشد؛ دوباره تلاش کنید. جزئیات در گزارش ثبت شده است.");
            return r.Output.Trim();
        }
        if(await Run("shell","id","-u")!="0")
        {
            await Run("root");await Run("wait-for-device");
            if(await Run("shell","id","-u")!="0")throw new IOException("دسترسی لازم به خودرو برقرار نشد؛ دوباره تلاش کنید.");
        }
        const string pkg="com.abrar.voyahiran";
        var path=await Run("shell","pm","path","--user","0",pkg);
        if(!path.StartsWith("package:/"))throw new IOException("ابتدا وویا ایران را نصب کنید و دوباره تلاش کنید.");
        if(!int.TryParse(await Run("shell","getprop","ro.build.version.sdk"),out var sdk) || sdk is <28 or >32)
            throw new IOException("نسخهٔ اندروید برای اعطای دسترسی تأیید نشد.");
        var permissions=new List<string>{"ACCESS_COARSE_LOCATION","ACCESS_FINE_LOCATION","RECORD_AUDIO","READ_EXTERNAL_STORAGE"};
        if(sdk<=29)permissions.Add("WRITE_EXTERNAL_STORAGE");
        var canClearFlags=true;
        foreach(var permission in permissions)
        {
            var full="android.permission."+permission;
            if(canClearFlags)
            {
                var flags=await adb.RunAsync(serial,["shell","pm","clear-permission-flags","--user","0",pkg,full,"user-set","user-fixed"],30);
                // Some OEM PackageManager shells do not expose this optional command.
                // Root pm grant remains available; verify every actual grant below.
                if(Regex.IsMatch(flags.Output.Trim(),@"\AUnknown command: clear-permission-flags\z",RegexOptions.IgnoreCase))
                {
                    canClearFlags=false;
                    log("Permission flag clearing unavailable on this firmware; using root grants with verification.");
                }
                else if(flags.ExitCode!=0 || Regex.IsMatch(flags.Output,@"(?i)\b(exception|error:|unknown operation)"))
                    throw new IOException("پاک‌سازی وضعیت دسترسی کامل نشد؛ جزئیات در گزارش ثبت شده است.");
            }
            await Run("shell","pm","grant","--user","0",pkg,full);
        }
        var dump=await Run("shell","dumpsys","package",pkg);
        var user=Regex.Match(dump,@"(?ms)^\s*User 0:.*?(?=^\s*User \d+:|\z)").Value;
        foreach(var permission in permissions)
            if(!Regex.IsMatch(user,Regex.Escape("android.permission."+permission)+@": granted=true\b"))
                throw new IOException("دسترسی "+permission+" تأیید نشد؛ دوباره تلاش کنید.");
        var ops=new List<string>{"COARSE_LOCATION","FINE_LOCATION","RECORD_AUDIO","READ_EXTERNAL_STORAGE","REQUEST_INSTALL_PACKAGES","SYSTEM_ALERT_WINDOW"};
        if(sdk<=29)ops.Add("WRITE_EXTERNAL_STORAGE");
        if(sdk>=30)ops.Add("MANAGE_EXTERNAL_STORAGE");
        foreach(var op in ops)
        {
            await Run("shell","cmd","appops","set","--user","0",pkg,op,"allow");
            var value=await Run("shell","cmd","appops","get","--user","0",pkg,op);
            if(!IsAppOpAllowed(value,op))
                throw new IOException("دسترسی "+op+" تأیید نشد؛ دوباره تلاش کنید.");
        }
        log("Voyah Iran permissions granted and verified for user 0.");
    }
}
