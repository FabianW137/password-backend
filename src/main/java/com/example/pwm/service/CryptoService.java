package com.example.pwm.service;

import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CryptoService {
  private final byte[] key;
  private final SecureRandom rnd = new SecureRandom();
  public CryptoService(org.springframework.core.env.Environment env) {
    String k = env.getProperty("crypto.key");
    if (k == null) throw new IllegalStateException("APP_ENCRYPTION_KEY fehlt");
    byte[] raw = java.util.Base64.getDecoder().decode(k);
    if (raw.length != 32) throw new IllegalStateException("APP_ENCRYPTION_KEY muss 32 Bytes sein");
    this.key = raw;
  }
  public String encrypt(String plain) {
    if (plain == null) return null;
    try {
      byte[] iv = new byte[12]; rnd.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key,"AES"), new GCMParameterSpec(128, iv));
      byte[] ct = c.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      byte[] out = new byte[iv.length+ct.length];
      System.arraycopy(iv,0,out,0,iv.length); System.arraycopy(ct,0,out,iv.length,ct.length);
      return java.util.Base64.getEncoder().encodeToString(out);
    } catch(Exception e){ throw new RuntimeException(e); }
  }
  public String decrypt(String enc) {
    if (enc == null) return null;
    try {
      byte[] all = java.util.Base64.getDecoder().decode(enc);
      byte[] iv = java.util.Arrays.copyOfRange(all,0,12);
      byte[] ct = java.util.Arrays.copyOfRange(all,12,all.length);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key,"AES"), new GCMParameterSpec(128, iv));
      return new String(c.doFinal(ct), java.nio.charset.StandardCharsets.UTF_8);
    } catch(Exception e){ throw new RuntimeException(e); }
  }
}
