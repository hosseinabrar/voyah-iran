using System.Text.RegularExpressions;

namespace VoyahIran.Installer.Core;

public static class VoyahPermissions
{
    public static async Task GrantAsync(IAdbClient adb, string serial, Action<string> log)
    {
        async Task<string> Run(params string[] args)
        {
            var r=await adb.RunAsync(serial,args,30);
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
        foreach(var permission in permissions)
        {
            var full="android.permission."+permission;
            await Run("shell","pm","clear-permission-flags","--user","0",pkg,full,"user-set","user-fixed");
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
            if(!Regex.IsMatch(value,@"\b"+Regex.Escape(op)+@":\s*allow\b"))
                throw new IOException("دسترسی "+op+" تأیید نشد؛ دوباره تلاش کنید.");
        }
        log("Voyah Iran permissions granted and verified for user 0.");
    }
}
