package com.abrar.raha.intent;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Versioned authenticated chunks: bounded temporary memory, no plaintext cache. */
public final class RahaSealedCodec {
    private static final int MAGIC = 0x52414831, CHUNK = 65536, MAX = 160 * 1024 * 1024;
    private static Cipher cipher(int mode, byte[] key, byte[] nonce, String name, int size, int index)
            throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        c.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        c.updateAAD(ByteBuffer.allocate(12).putInt(MAGIC).putInt(size).putInt(index).array());
        return c;
    }
    public static void seal(InputStream source, OutputStream target, int size, byte[] key, String name)
            throws IOException, GeneralSecurityException {
        if (size <= 0 || size > MAX) throw new IOException("Invalid asset size");
        DataOutputStream out = new DataOutputStream(target);
        out.writeInt(MAGIC); out.writeInt(size);
        byte[] buffer = new byte[CHUNK]; SecureRandom random = new SecureRandom();
        try {
            for (int offset = 0, index = 0; offset < size; index++) {
                int length = Math.min(CHUNK, size - offset);
                new DataInputStream(source).readFully(buffer, 0, length);
                byte[] nonce = new byte[12]; random.nextBytes(nonce);
                byte[] encrypted = cipher(Cipher.ENCRYPT_MODE,key,nonce,name,size,index).doFinal(buffer,0,length);
                out.write(nonce); out.write(encrypted); offset += length;
            }
            if (source.read() != -1) throw new IOException("Asset changed while sealing");
        } finally { Arrays.fill(buffer, (byte)0); }
    }
    public static byte[] open(InputStream source, byte[] key, String name) throws IOException, GeneralSecurityException {
        DataInputStream in = new DataInputStream(source);
        if (in.readInt() != MAGIC) throw new IOException("Invalid sealed asset");
        int size = in.readInt();
        if (size <= 0 || size > MAX) throw new IOException("Invalid asset size");
        byte[] result = new byte[size]; boolean success = false;
        try {
            for (int offset = 0, index = 0; offset < size; index++) {
                int length = Math.min(CHUNK, size - offset);
                byte[] nonce = new byte[12], encrypted = new byte[length + 16];
                in.readFully(nonce); in.readFully(encrypted);
                byte[] plain = cipher(Cipher.DECRYPT_MODE,key,nonce,name,size,index).doFinal(encrypted);
                try { System.arraycopy(plain,0,result,offset,length); }
                finally { Arrays.fill(plain,(byte)0); }
                offset += length;
            }
            if (in.read() != -1) throw new IOException("Trailing asset data");
            success = true; return result;
        } finally { if (!success) Arrays.fill(result,(byte)0); }
    }
}
