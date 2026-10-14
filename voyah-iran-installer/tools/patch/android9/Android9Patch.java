import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import java.security.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.*;
import com.android.tools.smali.dexlib2.dexbacked.instruction.*;
import com.android.tools.smali.dexlib2.writer.pool.*;
import com.android.tools.smali.baksmali.*;
import com.android.tools.smali.smali.*;

/** Local-only Android 9 reconstruction and exact-method patch verification. */
class Android9Patch {
    // Reviewed source pairs: never accept a converted DEX merely because its class count matches.
    static final Map<String,String> REVIEWED_DEX = Map.of(
        "a9303263150cc12ee9e941c9d295a3bf0aa8282284b9c5b15adc6efd59c41c06", "90d3c5c8fb3d7ee0e0ac11ac0fc6b996351e0fc3d02b24f2f1a12e79ae3f6138",
        "664c053504beb67f977132419b2a8080b207965f958530b9366d84758ead7299", "d0b537189af5389b272bebbb444f85af8d7105ff9929f923d5415979e893b931");
    static final Pattern METHOD = Pattern.compile("(?ms)^\\.method ([^\\n]+)\\n.*?^\\.end method");
    static final String AM = "Lcom/android/server/am/ActivityRecord;";
    static final String DISPLAY = "Lcom/android/server/am/VoyahIranLegacyDisplay;";
    static final String FRAMES = "Lcom/android/server/wm/VoyahIranLegacyFrames;";
    static final String IME = "Lcom/android/server/InputMethodManagerService;";
    static void check(boolean ok,String message) { if(!ok)throw new IllegalStateException(message); }
    static String text(Path p)throws Exception {return Files.readString(p).replace("\r\n","\n");}
    static String hash(Path p)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    static String normalize(String s) {
        StringBuilder out=new StringBuilder();
        for(String line:s.split("\n")) {
            String t=line.trim();
            if(t.isEmpty()||t.startsWith("#")||t.matches("\\.(line|local|param|source)\\b.*")||t.startsWith(".end local")||t.startsWith(".restart local"))continue;
            out.append(t.replace("const-string/jumbo ","const-string ")).append('\n');
        }return out.toString();
    }
    static String canonical(String s) {
        var methods=new TreeMap<String,String>();var m=METHOD.matcher(s);
        while(m.find())check(methods.put(m.group(1),normalize(m.group()))==null,"duplicate method");
        return normalize(METHOD.matcher(s).replaceAll(""))+methods;
    }
    static Map<String,Path> classes(Path root)throws Exception {
        var result=new TreeMap<String,Path>();try(var files=Files.walk(root)){
            for(var p:files.filter(f->f.toString().endsWith(".smali")).toList())result.put(root.relativize(p).toString().replace('\\','/'),p);
        }return result;
    }
    static void decode(Path dex,Path out)throws Exception {
        var d=new DexBackedDexFile(Opcodes.forApi(28),Files.readAllBytes(dex));
        var o=new BaksmaliOptions();o.apiLevel=28;o.sequentialLabels=true;
        check(Baksmali.disassembleDexFile(d,out.toFile(),Math.min(4,Runtime.getRuntime().availableProcessors()),o),"decode failed");
    }
    static String replaceOnce(String code,String needle,String value) {
        check(code.indexOf(needle)>=0&&code.indexOf(needle)==code.lastIndexOf(needle),"ambiguous hook: "+needle);
        return code.replace(needle,value);
    }
    static String patched(String id,String original) {
        switch(id) {
        case "InputMethodManagerService-updateInputMethodsFromSettingsLocked":
            String literal="    const-string v1, \"com.qinggan.app.qgime/.QingganIME\"";
            return replaceOnce(original,literal,literal+"\n\n    invoke-direct {p0, v1}, "+IME+"->voyahIranSelectedIme(Ljava/lang/String;)Ljava/lang/String;\n    move-result-object v1");
        case "ActivityRecord-updateOverrideConfiguration":
            String call="    invoke-direct {p0, v0}, "+AM+"->computeBounds(Landroid/graphics/Rect;)V";
            String result=replaceOnce(original,call,call+"\n\n    invoke-static {p0, v0}, "+DISPLAY+"->bounds("+AM+"Landroid/graphics/Rect;)V");
            String notify="    invoke-virtual {p0, v1}, "+AM+"->onOverrideConfigurationChanged(Landroid/content/res/Configuration;)V";
            return replaceOnce(result,notify,"    invoke-static {p0, v1}, "+DISPLAY+"->configuration("+AM+"Landroid/content/res/Configuration;)V\n\n"+notify);
        case "ActivityRecord-getRequestedOrientation":
            return original.replace("    return v0","    invoke-static {p0, v0}, "+DISPLAY+"->orientation(Ljava/lang/Object;I)I\n    move-result v0\n    return v0");
        case "AppWindowToken-getOrientation":
        case "AppWindowToken-getOrientationIgnoreVisibility":
            return original.replace("    return v0","    invoke-static {p0, v0}, "+FRAMES+"->orientation(Lcom/android/server/wm/AppWindowToken;I)I\n    move-result v0\n    return v0");
        case "AppWindowToken-layoutLetterbox":
            String marker="    .local v2, \"needsLetterbox\":Z";
            return replaceOnce(original,marker,marker+"\n    invoke-static {p0}, "+FRAMES+"->applies(Lcom/android/server/wm/AppWindowToken;)Z\n    move-result v3\n    if-eqz v3, :voyah_letterbox_original\n    const/4 v2, 0x0\n    :voyah_letterbox_original\n    const/4 v3, 0x0");
        case "WindowState-applyGravityAndUpdateFrame":
            // p0,p1,p2 are high registers; /range preserves the original register layout.
            String first="    move-object/from16 v0, p0";
            String start="    invoke-static/range {p0 .. p2}, "+FRAMES+"->before(Lcom/android/server/wm/WindowState;Landroid/graphics/Rect;Landroid/graphics/Rect;)V\n\n";
            return replaceOnce(original,first,start+first).replace("    return-void","    invoke-static/range {p0 .. p0}, "+FRAMES+"->after(Lcom/android/server/wm/WindowState;)V\n    return-void");
        default:throw new IllegalArgumentException(id);
        }
    }
    static final String[][] TARGETS={
        {"com/android/server/InputMethodManagerService.smali","InputMethodManagerService-updateInputMethodsFromSettingsLocked","updateInputMethodsFromSettingsLocked(Z)V"},
        {"com/android/server/am/ActivityRecord.smali","ActivityRecord-updateOverrideConfiguration","updateOverrideConfiguration()V"},
        {"com/android/server/am/ActivityRecord.smali","ActivityRecord-getRequestedOrientation","getRequestedOrientation()I"},
        {"com/android/server/wm/AppWindowToken.smali","AppWindowToken-getOrientation","getOrientation(I)I"},
        {"com/android/server/wm/AppWindowToken.smali","AppWindowToken-getOrientationIgnoreVisibility","getOrientationIgnoreVisibility()I"},
        {"com/android/server/wm/AppWindowToken.smali","AppWindowToken-layoutLetterbox","layoutLetterbox(Lcom/android/server/wm/WindowState;)V"},
        {"com/android/server/wm/WindowState.smali","WindowState-applyGravityAndUpdateFrame","applyGravityAndUpdateFrame(Landroid/graphics/Rect;Landroid/graphics/Rect;)V"}
    };
    static String method(String code,String signature) {
        var m=METHOD.matcher(code);String found=null;
        while(m.find())if(m.group(1).endsWith(signature)){check(found==null,"duplicate target");found=m.group();}
        check(found!=null,"method missing: "+signature);return found;
    }
    public static void main(String[] args)throws Exception {
        if(args.length==3 && args[0].equals("--decode-helper")) { decode(Path.of(args[1]),Path.of(args[2])); return; }
        check(args.length==4,"Usage: Android9Patch.java templates input.jar services.vdex-or-none new-work-directory");
        Path templates=Path.of(args[0]),input=Path.of(args[1]),vdex=Path.of(args[2]),job=Path.of(args[3]);
        check(!Files.exists(job),"job already exists");Files.createDirectories(job);
        String originalHash=hash(input);
        var entries=new TreeMap<String,byte[]>();
        try(var zip=new ZipFile(input.toFile())) {
            var it=zip.entries();while(it.hasMoreElements()) {
                var e=it.nextElement();check(e.getSize()>=0&&e.getSize()<128*1024*1024,"invalid ZIP size");
                check(!e.getName().contains("..")&&!e.getName().startsWith("/")&&!e.getName().contains("\\"),"invalid ZIP path");
                try(var in=zip.getInputStream(e)){check(entries.put(e.getName(),in.readAllBytes())==null,"duplicate ZIP entry");}
            }
        }
        Path dex=job.resolve("original.dex");
        boolean reconstructed=!entries.containsKey("classes.dex");
        if(reconstructed) {
            check(originalHash.equals("1c16f5976318fadcf7add92c518a93896f99e2a78f742b7347d8539103264882"),"Unsupported stripped JAR");
            String expectedDex=REVIEWED_DEX.get(hash(vdex));
            check(expectedDex!=null,"Unsupported Android 9 VDEX; no vehicle changes made");
            H97Unquicken.main(new String[]{vdex.toString(),dex.toString()});
            check(hash(dex).equals(expectedDex),"Reconstructed DEX differs from verified firmware");
        }else Files.write(dex,entries.get("classes.dex"));
        System.out.println("ANDROID9 decoding verified framework...");
        Path source=job.resolve("source");decode(dex,source);
        var before=classes(source);check(Set.of(4713,4714,4717,4718).contains(before.size()),"Unexpected class count");
        boolean already=before.containsKey("com/android/server/am/VoyahIranLegacyDisplay.smali");
        Path accepted=templates;
        if(already) for(String version:List.of("previous-v1","previous-v2","previous-v3","previous-v4","previous-v5","previous-v6", "previous-welcome", "previous-welcome-variant", "previous-merge-virtual", "previous-merge-production")) {
            Path candidate=templates.resolve(version);
            if(classes(candidate.resolve("helpers")).entrySet().stream().allMatch(e -> { try { return before.containsKey(e.getKey()) && canonical(text(before.get(e.getKey()))).equals(canonical(text(e.getValue()))); } catch(Exception ex) { return false; } })) { accepted=candidate; break; }
        }
        boolean upgrade=already && !accepted.equals(templates);
        var expected=new TreeMap<String,String>();for(var e:before.entrySet())expected.put(e.getKey(),text(e.getValue()));
        for(var target:TARGETS) {
            String code=expected.get(target[0]);check(code!=null,"missing target class");
            String actual=method(code,target[2]),original=text(templates.resolve(target[1]+".original.smali")).strip();
            String modified=patched(target[1],original);
            check(normalize(actual).equals(normalize(already?text(accepted.resolve(target[1]+".patched.smali")).strip():original)),"Unsupported method: "+target[1]);
            if(!already || upgrade)expected.put(target[0],code.replace(actual,modified));
        }
        String imeKey="com/android/server/InputMethodManagerService.smali";
        String helper=text(templates.resolve("helper-method.smali")).strip();
        if(already)check(normalize(method(expected.get(imeKey),"voyahIranSelectedIme(Ljava/lang/String;)Ljava/lang/String;")).equals(normalize(helper)),"IME helper differs");
        else expected.put(imeKey,expected.get(imeKey)+"\n\n"+helper+"\n");
        for(var e:classes(templates.resolve("helpers")).entrySet()) {
            String helperCode=text(e.getValue());
            if(already) {
                check(expected.containsKey(e.getKey())&&canonical(expected.get(e.getKey())).equals(canonical(text(accepted.resolve("helpers").resolve(e.getKey())))),"display helper differs");
                if(upgrade)expected.put(e.getKey(),helperCode);
            }
            else {check(!expected.containsKey(e.getKey()),"helper collision");expected.put(e.getKey(),helperCode);}
        }
        Path output=job.resolve("patched.jar");
        already = already && !upgrade;
        if(already)Files.copy(input,output);
        else {
            for(var e:expected.entrySet()){Path p=source.resolve(e.getKey());Files.createDirectories(p.getParent());Files.writeString(p,e.getValue());}
            System.out.println("ANDROID9 assembling "+expected.size()+" classes...");
            Path patchedDex=job.resolve("patched.dex");var so=new SmaliOptions();so.apiLevel=28;so.jobs=4;so.outputDexFile=patchedDex.toString();
            check(Smali.assemble(so,source.toString()),"assembly failed");
            Path verify=job.resolve("verify");decode(patchedDex,verify);var after=classes(verify);
            check(after.keySet().equals(expected.keySet()),"class set changed");
            // Re-assembly adjusts branch label numbering in modified methods. Compare those
            // to the reviewed post-assembly templates; every other method must stay identical.
            for(var t:TARGETS){String s=expected.get(t[0]);expected.put(t[0],s.replace(method(s,t[2]),text(templates.resolve(t[1]+".patched.smali")).strip()));}
            for(var e:after.entrySet())check(canonical(text(e.getValue())).equals(canonical(expected.get(e.getKey()))),"Verification differs: "+e.getKey());
            entries.put("classes.dex",Files.readAllBytes(patchedDex));
            try(var out=new ZipOutputStream(Files.newOutputStream(output))) {for(var e:entries.entrySet()){
                var ze=new ZipEntry(e.getKey());ze.setTime(0);out.putNextEntry(ze);out.write(e.getValue());out.closeEntry();
            }}
        }
        String patchedHash=hash(output);
        Files.writeString(job.resolve("result.txt"),"original="+originalHash+"\npatched="+patchedHash+"\nalready="+already+"\n",StandardCharsets.UTF_8);
        System.out.println("VOYAH_PATCH_VERIFIED original="+originalHash+" patched="+patchedHash+" already="+already);
    }
}

// VDEX 019 decoding adapted from vdexExtractor by Anestis Bechtsoudis.
// Copyright 2017 - 2018 CENSUS S.A. Licensed under Apache-2.0; see VDEX-LICENSE.txt.
// This Java adaptation is modified from the original C implementation.
class H97Unquicken {
 static byte[] b; static int quick,table,data,min; static final Opcodes OPS=Opcodes.forApi(28);
 static int u(int o){return (b[o]&255)|((b[o+1]&255)<<8)|((b[o+2]&255)<<16)|((b[o+3]&255)<<24);}
 static int h(int o){return (b[o]&255)|((b[o+1]&255)<<8);}
 static void put(int o,int v){b[o]=(byte)v;b[o+1]=(byte)(v>>8);}
 static int leb(int[] p){int v=0;for(int n=0;n<5;n++){int x=b[p[0]++]&255;v|=(x&127)<<(7*n);if(x<128)return v;}throw new IllegalStateException("leb");}
 static void check(boolean v,String s){if(!v)throw new IllegalStateException(s);}
 static int qoffset(int idx){int p=data+u(table+(idx/16)*4);int mask=((b[p]&255)<<8)|(b[p+1]&255);int bit=idx%16;if((mask&(1<<bit))==0)return 0;int n=Integer.bitCount(mask&((1<<(bit+1))-1)),v=min;int[] pos={p+2};while(n-->0)v+=leb(pos);return v;}
 public static void main(String[] args)throws Exception {
 b=Files.readAllBytes(Path.of(args[0]));check(new String(b,0,7).equals("vdex019") && b[7]==0 && new String(b,8,3).equals("002") && b[11]==0,"version");check(u(12)==1,"single dex required");
 int dexoff=40;check(CDexBackedDexFile.isCdex(b,dexoff),"cdex");quick=36+u(24)+u(28)+u(16);check(quick+u(32)==b.length,"sections");
 int qtable=quick+u(36);min=u(qtable);data=qtable+8;table=data+u(qtable+4);
 var dex=new CDexBackedDexFile(OPS,b,dexoff);var seen=new HashSet<Integer>();var counts=new TreeMap<String,Integer>();int used=0;
 for(var c:dex.getClasses())for(var m:c.getMethods()){
 var impl=m.getImplementation();if(impl==null)continue;var ins=new ArrayList<DexBackedInstruction>();for(var i:impl.getInstructions())ins.add((DexBackedInstruction)i);if(ins.isEmpty()||!seen.add(ins.get(0).instructionStart))continue;
 int qo=qoffset(m.methodIndex),n=0;int[] pos={quick+qo-1};if(qo!=0)n=leb(pos);int index=0,skip=-1;
 for(var i:ins){int off=dex.getDataBuffer().getBaseOffset()+i.instructionStart;if(off<skip)continue;var op=i.getOpcode();String name=op.name();if(op.odexOnly())counts.merge(name,1,Integer::sum);
 if(name.equals("RETURN_VOID_NO_BARRIER")){b[off]=(byte)(short)OPS.getOpcodeValue(Opcode.RETURN_VOID);continue;}
 if((op==Opcode.NOP || op==Opcode.ARRAY_PAYLOAD || op==Opcode.PACKED_SWITCH_PAYLOAD || op==Opcode.SPARSE_SWITCH_PAYLOAD) && n>0){check(index<n,"nop index");int reg=h(pos[0]+2*index++);if(reg==65535)continue;check(index<n&&reg<256,"cast");int type=h(pos[0]+2*index++);b[off]=(byte)(short)OPS.getOpcodeValue(Opcode.CHECK_CAST);b[off+1]=(byte)reg;put(off+2,type);skip=off+4;continue;}
 if(op.odexOnly()){
 check(index<n,"quick index "+name+" "+m);String target=name.replace("_QUICK","");var replacement=Opcode.valueOf(target);b[off]=(byte)(short)OPS.getOpcodeValue(replacement);put(off+2,h(pos[0]+2*index++));
 }
 }
 if(index!=n) {System.out.println("counts="+counts+" qo="+qo+" ptr="+pos[0]+" idx="+m.methodIndex); for(var z:ins)System.out.println(z.instructionStart+" "+z.getOpcode());} check(index==n,"unused indices "+m+" "+index+"/"+n);used+=index;
 }
 System.out.println("restored="+counts+" indices="+used);
 dex=new CDexBackedDexFile(OPS,b,dexoff);for(var c:dex.getClasses())for(var m:c.getMethods()){var impl=m.getImplementation();if(impl!=null)for(var i:impl.getInstructions())check(!i.getOpcode().odexOnly(),"optimized opcode remains");}
 DexPool.writeTo(args[1],dex);System.out.println("DEX written classes="+dex.getClasses().size());
 }
}
