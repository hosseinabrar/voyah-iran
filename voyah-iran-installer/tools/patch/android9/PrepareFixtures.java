import java.nio.file.*;
import java.util.*;
import com.android.tools.smali.smali.*;
/** Developer-only fixture generation. Review the generated diff before shipping. */
class PrepareFixtures {
 public static void main(String[] a)throws Exception {
  Path original=Path.of(a[0]),templates=Path.of(a[1]),job=Path.of(a[2]);
  Android9Patch.check(!Files.exists(job),"new job required");Files.createDirectories(job);
  var expected=new TreeMap<String,String>();for(var e:Android9Patch.classes(original).entrySet())expected.put(e.getKey(),Android9Patch.text(e.getValue()));
  for(var t:Android9Patch.TARGETS){String code=expected.get(t[0]),m=Android9Patch.method(code,t[2]);
   Files.writeString(templates.resolve(t[1]+".original.smali"),m+"\n");
   expected.put(t[0],code.replace(m,Android9Patch.patched(t[1],m)));
  }
  String ime="com/android/server/InputMethodManagerService.smali";
  expected.put(ime,expected.get(ime)+"\n"+Android9Patch.text(templates.resolve("helper-method.smali")));
  for(var e:Android9Patch.classes(templates.resolve("helpers")).entrySet())expected.put(e.getKey(),Android9Patch.text(e.getValue()));
  for(var e:expected.entrySet()){Path p=job.resolve("source").resolve(e.getKey());Files.createDirectories(p.getParent());Files.writeString(p,e.getValue());}
  var o=new SmaliOptions();o.apiLevel=28;o.jobs=4;o.outputDexFile=job.resolve("patched.dex").toString();
  Android9Patch.check(Smali.assemble(o,job.resolve("source").toString()),"assembly");
  Android9Patch.decode(job.resolve("patched.dex"),job.resolve("verify"));
  for(var t:Android9Patch.TARGETS)Files.writeString(templates.resolve(t[1]+".patched.smali"),Android9Patch.method(Android9Patch.text(job.resolve("verify").resolve(t[0])),t[2])+"\n");
  System.out.println("Fixtures generated. Run Android9Patch for full unchanged-class verification.");
 }
}
