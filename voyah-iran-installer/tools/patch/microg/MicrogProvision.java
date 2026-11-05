import android.content.Context;
import android.content.pm.PackageManager;
import java.lang.reflect.*;

/** Executed by root app_process; no app lifecycle, accounts or tokens are read. */
public final class MicrogProvision {
    public static void main(String[] args) throws Exception {
        if (android.os.Process.myUid()!=0) throw new SecurityException("Root required");
        android.os.Looper.prepareMainLooper();
        Class<?> at=Class.forName("android.app.ActivityThread");
        Object thread=at.getMethod("systemMain").invoke(null);
        Context context=(Context)at.getMethod("getSystemContext").invoke(thread);
        Object binder=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"user");
        Object user=Class.forName("android.os.IUserManager$Stub").getMethod("asInterface",Class.forName("android.os.IBinder")).invoke(null,binder);
        Class<?> iface=Class.forName("android.os.IUserManager");
        iface.getMethod("setUserRestriction",String.class,boolean.class,int.class).invoke(user,"no_modify_accounts",false,0);
        if ((Boolean)iface.getMethod("hasUserRestriction",String.class,int.class).invoke(user,"no_modify_accounts",0))
            throw new IllegalStateException("Account restriction remains active");
        preparePackageVisibility();
        PackageManager pm=context.getPackageManager();
        try { pm.getPackageInfo("com.google.android.gms",0); }
        catch(PackageManager.NameNotFoundException missing) { System.out.println("MICROG_ABSENT"); return; }
        // This API checks the stored signing identity, not our PackageInfo presentation.
        String hex="9bd06727e62796c0130eb6dab39b73157451582cbd138e86c468acc395d14165";
        byte[] cert=new byte[32]; for(int i=0;i<32;i++)cert[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
        if(!pm.hasSigningCertificate("com.google.android.gms",cert,PackageManager.CERT_INPUT_SHA256)) {
            System.out.println("MICROG_NON_OFFICIAL_SKIPPED"); return;
        }
        if(args.length>0 && args[0].equals("verify")) {
            byte[] sig=pm.getPackageInfo("com.google.android.gms",PackageManager.GET_SIGNING_CERTIFICATES).signingInfo.getApkContentsSigners()[0].toByteArray();
            byte[] digest=java.security.MessageDigest.getInstance("SHA-1").digest(sig);
            StringBuilder result=new StringBuilder(); for(byte b:digest) result.append(String.format("%02x",b & 255));
            if(!result.toString().equals("38918a453d07199354f8b19af05ec6562ced5788"))throw new IllegalStateException("microG spoofing verification failed");
        }
        System.out.println("MICROG_OFFICIAL_READY");
    }
    private static void preparePackageVisibility() throws Exception {
        if(android.os.Build.VERSION.SDK_INT<30)return;
        if(!"false".equals(config("get","package_manager_service","package_query_filtering_enabled")))
            config("put","package_manager_service","package_query_filtering_enabled","false");
        if(!"false".equals(config("get","package_manager_service","package_query_filtering_enabled")))
            throw new IllegalStateException("Package visibility not verified");
        System.out.println("PACKAGE_VISIBILITY_UNRESTRICTED");
    }
    private static String readOutput(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        byte[] buffer=new byte[1024];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>8192)throw new java.io.IOException("Excessive DeviceConfig output");out.write(buffer,0,n);}
        return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
    }
    private static String config(String... args) throws Exception {
        java.util.List<String> command=new java.util.ArrayList<>();
        command.add("/system/bin/device_config");java.util.Collections.addAll(command,args);
        Process p=new ProcessBuilder(command).redirectErrorStream(true).start();
        if(!p.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)){p.destroyForcibly();throw new IllegalStateException("DeviceConfig timed out");}
        String output=readOutput(p.getInputStream()).trim();
        if(p.exitValue()!=0)throw new IllegalStateException("DeviceConfig failed");
        return output;
    }
}
