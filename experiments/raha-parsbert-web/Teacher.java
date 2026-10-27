import com.abrar.raha.*;
import com.abrar.voicecommands.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Offline annotation only. The browser never invokes the production parser. */
public class Teacher {
    static String json(Object value) {
        if(value==null)return "null";
        if(value instanceof Number || value instanceof Boolean)return value.toString();
        if(value instanceof Map){List<String> pairs=new ArrayList<>();((Map<?,?>)value).forEach((k,v)->pairs.add(json(k)+":"+json(v)));return "{"+String.join(",",pairs)+"}";}
        return "\""+value.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";
    }
    public static void main(String[] args)throws Exception {
        if(args.length>0 && args[0].equals("vocabulary")) {for(String s:RahaSpeechVocabulary.INSTANCE.phrases())System.out.println(s);return;}
        RahaCommandRecognizer parser=new RahaCommandRecognizer();
        // This corpus contains single commands. Skip the compound-command fallback
        // (quadratic splitting on deliberately invalid alias/operator combinations).
        java.lang.reflect.Method single=RahaCommandRecognizer.class.getDeclaredMethod("recognizeSingle",String.class);
        single.setAccessible(true);
        BufferedReader input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
        String line;
        while((line=input.readLine())!=null){
            Recognition r=(Recognition)single.invoke(parser,line);
            Map<String,Object> out=new LinkedHashMap<>();out.put("text",line);
            if(r instanceof Recognition.Matched){VehicleCommand c=((Recognition.Matched)r).getCommand();out.put("action",c.getAction());out.put("parameters",c.getParameters());out.put("description",c.getDescription());}
            else {out.put("action","UNKNOWN");out.put("parameters",Collections.emptyMap());}
            System.out.println(json(out));
        }
    }
}
