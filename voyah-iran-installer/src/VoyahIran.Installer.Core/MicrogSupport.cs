namespace VoyahIran.Installer.Core;

/// <summary>Prepares installed official microG without replacing GMS or user account data.</summary>
public static class MicrogSupport
{
    public static readonly string[] Assets = [
        "patch/microg/VoyahMicrogCompat.smali", "patch/microg/legacy-device-helper.smali",
        "patch/microg/android11-generatePackageInfo.original.smali", "patch/microg/android9-generatePackageInfo.original.smali",
        "patch/microg/provision.dex", "patch/microg/LICENSE.txt"
    ];
    public static async Task PrepareAsync(IAdbClient adb, string serial, PayloadBundle bundle, Action<string> log)
    {
        async Task<string> Run(params string[] args)
        {
            var result=await new FrameworkServiceRecovery(adb,log).Run(serial,args,60);
            if(result.ExitCode!=0) throw new IOException("آماده‌سازی سرویس‌های گوگل کامل نشد؛ گزارش نصب را بررسی کنید.");
            return result.Output;
        }
        const string remote="/data/local/tmp/voyah-microg-provision.dex";
        await Run("push",bundle.File("patch/microg/provision.dex"),remote);
        var hash=await Run("shell","sha256sum",remote);
        if(!hash.StartsWith(bundle.Manifest.Files["patch/microg/provision.dex"]+" ",StringComparison.Ordinal))
            throw new IOException("MicroG provisioning helper integrity failed.");
        const string invoke="CLASSPATH="+remote+" app_process /system/bin MicrogProvision";
        var state=await Run("shell",invoke);
        if(state.Split('\n').Any(l=>l.Trim()=="MICROG_ABSENT" || l.Trim()=="MICROG_NON_OFFICIAL_SKIPPED"))
        {
            log("Google account support prepared; official microG is not installed. Existing Google services were not replaced.");
            return;
        }
        if(!state.Split('\n').Any(l=>l.Trim()=="MICROG_OFFICIAL_READY")) throw new IOException("MicroG identity could not be verified.");
        // Companion installation and location permissions belong to the app's event-driven
        // root worker, including when GmsCore is installed later. Do not race that worker.
        var verified=await Run("shell",invoke+" verify");
        if(!verified.Split('\n').Any(l=>l.Trim()=="MICROG_OFFICIAL_READY"))throw new IOException("MicroG runtime verification failed.");
        log("Official microG framework support verified; app worker prepares Companion and location permissions.");
    }
}
