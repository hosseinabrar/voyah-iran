import com.abrar.raha.intent.RahaSealedCodec;
import com.abrar.raha.intent.RahaAssetKey;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Host-only verification against the real generated key; prints no key/plaintext. */
public class VerifyRahaAssets {
    public static void main(String[] args) throws Exception {
        byte[] key=RahaAssetKey.value();
        try {
            for(String name:List.of("model.onnx","manifest.json","schema.json","vocab.txt","contact-rules.json")) {
                Path source=Path.of(args[0],"raha-intent",name), encrypted=Path.of(args[1],"raha-intent",name+".sealed");
                byte[] plain;
                try(var in=Files.newInputStream(encrypted)){plain=RahaSealedCodec.open(in,key,name);}
                try {
                    MessageDigest expected=MessageDigest.getInstance("SHA-256");
                    try(var in=new DigestInputStream(Files.newInputStream(source),expected)) { in.transferTo(java.io.OutputStream.nullOutputStream()); }
                    if(!MessageDigest.isEqual(expected.digest(),MessageDigest.getInstance("SHA-256").digest(plain))) throw new SecurityException("Round trip failed: "+name);
                    System.out.println("Verified sealed asset: "+name+" ("+plain.length+" bytes)");
                } finally { Arrays.fill(plain,(byte)0); }
                if(Files.exists(Path.of(args[1],"raha-intent",name)))throw new SecurityException("Plaintext still packaged");
            }
        } finally { Arrays.fill(key,(byte)0); }
    }
}
