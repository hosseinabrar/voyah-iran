import android.content.Context;
import android.content.pm.*;
import com.android.server.pm.VoyahMicrogCompat;
/** Optional root app_process test; reads an official microG APK, changes no installed package. */
public class MicrogCompatRuntimeTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        android.os.Looper.prepareMainLooper();
        Class<?> at=Class.forName("android.app.ActivityThread");
        Context context=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        PackageManager pm=context.getPackageManager();
        String apk=pm.getApplicationInfo("com.google.android.gms",0).sourceDir;
        PackageInfo info=pm.getPackageArchiveInfo(apk,PackageManager.GET_SIGNATURES|PackageManager.GET_SIGNING_CERTIFICATES);
        Signature real=info.signatures[0];
        info.packageName="unrelated.package";VoyahMicrogCompat.apply(info);check(real.equals(info.signatures[0]));
        info.packageName="com.google.android.gms";VoyahMicrogCompat.apply(info);check(!real.equals(info.signatures[0]));
        check(info.signatures[0].equals(info.signingInfo.getApkContentsSigners()[0]));
        PackageInfo untrusted=new PackageInfo();untrusted.packageName="com.google.android.gms";untrusted.signatures=new Signature[]{new Signature(new byte[]{1,2,3})};
        Signature[] original=untrusted.signatures;VoyahMicrogCompat.apply(untrusted);check(untrusted.signatures==original);
        PackageInfo legacy=pm.getPackageArchiveInfo(apk,PackageManager.GET_SIGNATURES);VoyahMicrogCompat.apply(legacy);check(!real.equals(legacy.signatures[0]));
        check(VoyahMicrogCompat.apply(null)==null);
        System.out.println("PASS: trusted legacy/modern presentation, unrelated/untrusted identity preserved, null safe");
    }
}
