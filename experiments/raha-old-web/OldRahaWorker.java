import com.abrar.raha.OldRahaRecognizer;
import com.abrar.voicecommands.*;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class OldRahaWorker {
    static String json(Object v) {
        if(v==null)return "null";
        if(v instanceof Number || v instanceof Boolean)return v.toString();
        if(v instanceof Map){List<String> p=new ArrayList<>();((Map<?,?>)v).forEach((k,x)->p.add(json(k)+":"+json(x)));return "{"+String.join(",",p)+"}";}
        return "\""+v.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";
    }
    public static void main(String[] args)throws Exception {
        System.setOut(new java.io.PrintStream(System.out,true,StandardCharsets.UTF_8));
        OldRahaRecognizer parser=new OldRahaRecognizer(()->{try{return new FileInputStream(args[0]);}catch(IOException e){throw new UncheckedIOException(e);}});
        parser.prepare();
        BufferedReader in=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
        String line;
        while((line=in.readLine())!=null) {
            if(line.equals("RELOAD")){parser.close();parser.prepare();System.out.println("{}");continue;}
            String text=new String(Base64.getDecoder().decode(line),StandardCharsets.UTF_8);
            Recognition r=parser.recognize(text);
            Map<String,Object> out=new LinkedHashMap<>();out.put("source",parser.getSource());
            if(r instanceof Recognition.Matched){VehicleCommand c=((Recognition.Matched)r).getCommand();out.put("action",c.getAction());out.put("parameters",c.getParameters());}
            else {out.put("action","UNKNOWN");out.put("parameters",Collections.emptyMap());out.put("reason",((Recognition.Unknown)r).getReason());}
            System.out.println(json(out));
        }
    }
}
