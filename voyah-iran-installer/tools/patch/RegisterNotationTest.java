import java.util.function.Function;
import java.nio.file.*;

/** Run against both standalone patchers with smali-runtime.jar on the classpath. */
class RegisterNotationTest {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    static String method(String signature, String directive) {
        return ".method " + signature + "\n    " + directive +
            "\n    const/4 v0, 0x0\n    return-void\n.end method\n";
    }
    public static void main(String[] args) throws Exception {
        for (Function<String,String> canonical : java.util.List.<Function<String,String>>of(
                FrameworkPatch::canonical, Android9Patch::canonical)) {
            String[] signatures = {"constructor <init>(Ljava/lang/Object;Landroid/os/Handler;Ljava/lang/Object;)V",
                "public onChange(Z)V", "public static run(JD[DLjava/lang/String;[[J)V",
                "public run(JD[DLjava/lang/String;[[J)V", "public static empty()V"};
            int[] parameters = {4, 2, 7, 8, 0};
            for (int i=0;i<signatures.length;i++) {
                String registers=method(signatures[i],".registers "+(parameters[i]+1));
                String locals=method(signatures[i],".locals 1");
                check(canonical.apply(registers).equals(canonical.apply(locals)));
                check(!canonical.apply(registers).equals(canonical.apply(locals.replace(".locals 1",".locals 2"))));
                check(!canonical.apply(registers).equals(canonical.apply(locals.replace("0x0","0x1"))));
            }
            String constructor=method(signatures[0],".registers 4");
            check(canonical.apply(constructor).equals(canonical.apply(constructor.replace(".registers 4",".locals 0"))));
            try { canonical.apply(method("public invalid(J)V",".registers 2")); throw new AssertionError(); }
            catch (IllegalStateException expected) {}
        }
        if(args.length==2) {
            Path source=Path.of(args[0]),verify=Path.of(args[1]);
            var before=FrameworkPatch.classes(source);var after=FrameworkPatch.classes(verify);
            check(before.keySet().equals(after.keySet()));
            for(String name:before.keySet())
                if(!FrameworkPatch.canonical(FrameworkPatch.text(before.get(name))).equals(
                    FrameworkPatch.canonical(FrameworkPatch.text(after.get(name)))))
                    throw new AssertionError("Real roundtrip mismatch: "+name);
            System.out.println("Real roundtrip verified: "+before.size()+" classes");
        }
        System.out.println("Register notation tests passed for Android 11 and Android 9.");
    }
}
