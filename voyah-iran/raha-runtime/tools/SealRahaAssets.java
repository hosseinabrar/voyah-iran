import com.abrar.raha.intent.RahaSealedCodec;
import java.nio.file.*;
import java.io.*;
import java.security.*;
import java.util.*;

/** Local build-only tool. Keys are generated into ignored build output, never logged. */
public class SealRahaAssets {
    public static void main(String[] args) throws Exception {
        Path source=Path.of(args[0]), target=Path.of(args[1]), javaRoot=Path.of(args[2]);
        byte[] key=new byte[32], share=new byte[32];
        SecureRandom rng=new SecureRandom();rng.nextBytes(key);rng.nextBytes(share);
        Set<String> sealed=Set.of("model.onnx","schema.json","manifest.json","vocab.txt","contact-rules.json");
        for(String name:sealed) if(!name.equals("model.onnx") && !Files.isRegularFile(source.resolve("raha-intent/"+name))) throw new IOException("Missing intent input: "+name);
        byte[] model=RahaModelDelta.restore(source.resolve("raha-intent"));
        String manifest=Files.readString(source.resolve("raha-intent/manifest.json"));
        var match=java.util.regex.Pattern.compile("\"model_sha256\"\\s*:\\s*\"([a-f0-9]{64})\"").matcher(manifest);
        if(!match.find() || !match.group(1).equals(HexFormat.of().formatHex(RahaModelDelta.hash(model))))throw new IOException("Model does not match verified manifest");
        Files.createDirectories(target.resolve("raha-intent"));
        try(var in=new ByteArrayInputStream(model);var out=Files.newOutputStream(target.resolve("raha-intent/model.onnx.sealed"))) {
            RahaSealedCodec.seal(in,out,model.length,key,"model.onnx");
        } finally {Arrays.fill(model,(byte)0);}
        try(var paths=Files.walk(source)) {
            for(Path path:(Iterable<Path>)paths.filter(Files::isRegularFile)::iterator) {
                Path relative=source.relativize(path);
                String normalized=relative.toString().replace('\\','/');
                if(normalized.equals("raha-intent/model.base.onnx") || normalized.equals("raha-intent/model.delta"))continue;
                if(normalized.equals("raha-intent/model.onnx"))throw new IOException("Unexpected legacy model input");
                boolean encrypt=normalized.startsWith("raha-intent/") && sealed.contains(path.getFileName().toString());
                Path output=target.resolve(relative.toString()+(encrypt?".sealed":""));
                Files.createDirectories(output.getParent());
                if(encrypt) {
                    try(var in=Files.newInputStream(path);var out=Files.newOutputStream(output)) {
                        RahaSealedCodec.seal(in,out,Math.toIntExact(Files.size(path)),key,path.getFileName().toString());
                    }
                } else Files.copy(path,output,StandardCopyOption.REPLACE_EXISTING);
            }
        }
        StringBuilder a=new StringBuilder(),b=new StringBuilder();
        for(int i=0;i<32;i++){if(i>0){a.append(',');b.append(',');}a.append(share[i]);b.append((byte)(share[i]^key[i]));}
        Path generated=javaRoot.resolve("com/abrar/raha/intent/RahaAssetKey.java");Files.createDirectories(generated.getParent());
        Files.writeString(generated,"package com.abrar.raha.intent; public final class RahaAssetKey { public static byte[] value() { byte[] a={"+a+"}, b={"+b+"}; for(int i=0;i<a.length;i++)a[i]^=b[i]; java.util.Arrays.fill(b,(byte)0); return a; } }");
        Arrays.fill(key,(byte)0);Arrays.fill(share,(byte)0);
    }
}
