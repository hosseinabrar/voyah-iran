import java.nio.file.*;
class LetterboxPatchTest {
    public static void main(String[] args) throws Exception {
        String original = FrameworkPatch.text(Path.of(args[0])).strip();
        String patched = DisplayPolicyPatch.patched("layoutLetterbox", original);
        String start = "    .locals 8\n";
        int guardStart = patched.indexOf(start) + start.length();
        int guardEnd = patched.indexOf("    :cond_1\n") + "    :cond_1\n".length();
        String renamed = original;
        for (int i=9;i>=0;i--) renamed=renamed.replace(":cond_"+i,":cond_"+Integer.toHexString(i+2));
        FrameworkPatch.require((patched.substring(0,guardStart-1) + patched.substring(guardEnd)).equals(renamed), "Native layout was altered");
        String guard = patched.substring(guardStart,guardEnd);
        FrameworkPatch.require(guard.indexOf("->applies(") < guard.indexOf("->destroy()V"), "Missing policy gate");
        FrameworkPatch.require(guard.contains("if-eqz v0, :cond_1"), "Disabled/OEM path must use native layout");
        FrameworkPatch.require(guard.contains("if-eqz v0, :cond_0"), "Null letterbox must be safe");
        FrameworkPatch.require(guard.indexOf("->destroy()V") < guard.indexOf("iput-object v0"), "Dispose input before clearing reference");
        FrameworkPatch.require(guard.contains("const/4 v0, 0x0"), "Reference must be cleared for repeated layouts");
        System.out.println("6 letterbox patch checks passed");
    }
}
