import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Build-only reconstruction; never shipped in the APK. */
public final class RahaModelDelta {
    static byte[] hash(byte[] data) throws Exception {return MessageDigest.getInstance("SHA-256").digest(data);}
    public static byte[] restore(Path directory) throws Exception {
        byte[] base=Files.readAllBytes(directory.resolve("model.base.onnx"));
        try(var in=new DataInputStream(Files.newInputStream(directory.resolve("model.delta")))) {
            if(!Arrays.equals(in.readNBytes(8),"RAHADLT1".getBytes(java.nio.charset.StandardCharsets.US_ASCII)) ||
                    !MessageDigest.isEqual(in.readNBytes(32),hash(base)))throw new IOException("Invalid Raha model base");
            byte[] expected=in.readNBytes(32);
            int length=in.readInt(),count=in.readInt();
            if(length<=0 || length>512*1024*1024 || count<0 || count>(length+65535)/65536)throw new IOException("Invalid delta size");
            byte[] result=Arrays.copyOf(base,length);Arrays.fill(base,(byte)0);
            int end=0;
            for(int i=0;i<count;i++) {
                int offset=in.readInt(),size=in.readInt();
                if(offset<end || size<=0 || size>65536 || offset>length-size)throw new IOException("Invalid delta block");
                in.readFully(result,offset,size);end=offset+size;
            }
            if(in.read()!=-1 || !MessageDigest.isEqual(expected,hash(result)))throw new IOException("Raha model hash mismatch");
            return result;
        }
    }
    public static void main(String[] args) throws Exception {Files.write(Path.of(args[1]),restore(Path.of(args[0])));}
}
