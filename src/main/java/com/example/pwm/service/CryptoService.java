package com.example.pwm.service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class CryptoService {
    private final byte[] key;
    private final SecureRandom rnd = new SecureRandom();
    public CryptoService() {
        String keyB64 = System.getenv("APP_ENCRYPTION_KEY");
        if (keyB64 == null || keyB64.isBlank()) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY muss Base64-kodiert gesetzt sein");
        }
        byte[] raw = Base64.getDecoder().decode(keyB64);
        if (raw.length != 32) throw new IllegalStateException("APP_ENCRYPTION_KEY muss 32 Bytes sein");
        this.key = raw;
    }
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[12];
            rnd.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(ct, 0, packed, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    public String decrypt(String b64) {
        if (b64 == null) return null;
        try {
            byte[] packed = Base64.getDecoder().decode(b64);
            byte[] iv = java.util.Arrays.copyOfRange(packed, 0, 12);
            byte[] ct = java.util.Arrays.copyOfRange(packed, 12, packed.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] pt = c.doFinal(ct);
            return new String(pt, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
