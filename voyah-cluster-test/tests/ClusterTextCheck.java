import com.abrar.voyahclustertest.TextProbe;
import com.abrar.voyahclustertest.ArabicForms;
public class ClusterTextCheck {
 public static void main(String[] args) {
  for(String s:new String[]{"\u0647\u0645\u062a","\u0645\u062f\u06cc\u0631\u06cc\u062a","\u067e \u0686 \u0698 \u06af \u06a9 \u06cc","\u0627\u0644\u0639\u0631\u0628\u064a\u0629","\u0647\u0645\u062a \u06f1\u06f2\u06f3","Hemmat 123"}) {
   String frame=TextProbe.encode(1,s);
   if(!frame.chars().allMatch(c->c<128))throw new AssertionError("non ASCII wire");
   if(!s.equals(TextProbe.decode(frame.split("\t",-1)[1])))throw new AssertionError("roundtrip");
  }
  if(!ArabicForms.shape("\u0647\u0645\u062a").equals("\uFEEB\uFEE4\uFE96"))throw new AssertionError("joining");
  if(!ArabicForms.shape("\u0628\u200c\u0628").equals("\uFE8F\u200C\uFE8F"))throw new AssertionError("ZWNJ");
  if(!ArabicForms.shape("\u0645\u062f\u06cc\u0631\u06cc\u062a").matches("[\\uFB50-\\uFEFF]+"))throw new AssertionError("Persian forms");
  System.out.println("PASS: Persian/Arabic/Latin transport, connected letters, Persian forms and ZWNJ");
 }
}
