package com.example.pwm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;


@Service
public class CryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;     
    private static final int IV_LEN = 12;            

    private final byte[] key;                       
    private final SecureRandom rnd = new SecureRandom();


    public CryptoService(
            @Value("${app.encryption.key-b64:${APP_ENCRYPTION_KEY:}}") String keyB64) {
        if (keyB64 == null || keyB64.isBlank()) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY / app.encryption.key-b64 muss gesetzt sein (Base64, 32 Bytes).");
        }
        byte[] raw = Base64.getDecoder().decode(keyB64);
        if (raw.length != 32) {
            throw new IllegalStateException("Verschlüsselungs-Key muss 32 Bytes (AES-256) haben.");
        }
        this.key = raw;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            rnd.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv)
            );

            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);

            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new RuntimeException("Encrypt failed", e);
        }
    }

    public String decrypt(String b64) {
        if (b64 == null) return null;
        try {
            byte[] in = Base64.getDecoder().decode(b64);
            if (in.length <= IV_LEN) {
                throw new IllegalArgumentException("Ciphertext zu kurz");
            }

            byte[] iv = new byte[IV_LEN];
            byte[] ct = new byte[in.length - IV_LEN];
            System.arraycopy(in, 0, iv, 0, IV_LEN);
            System.arraycopy(in, IV_LEN, ct, 0, ct.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv)
            );

            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decrypt failed", e);
        }
    }
}
