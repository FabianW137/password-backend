package com.example.pwm.controller;

import com.example.pwm.entity.*;
import com.example.pwm.repo.*;
import com.example.pwm.service.*;
import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.crypto.KeyGenerator;
import java.awt.image.BufferedImage;
import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import java.io.ByteArrayOutputStream;
import java.security.Key;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final UserAccountRepository users;
  private final VaultItemRepository items;
  private final CryptoService crypto;
  private final JwtService jwt;

  public ApiController(UserAccountRepository users, VaultItemRepository items, CryptoService crypto, JwtService jwt) {
    this.users = users; this.items = items; this.crypto = crypto; this.jwt = jwt;
  }

  public record RegisterReq(String email, String password) {}
  public record RegisterRes(String otpauthUrl, String qrImageDataUrl) {}

  @PostMapping("/auth/register")
  public RegisterRes register(@Valid @RequestBody RegisterReq req) throws Exception {
    var u = new UserAccount();
    u.setEmail(req.email().toLowerCase());
    u.setPasswordHash(org.springframework.security.crypto.bcrypt.BCrypt.hashpw(req.password(), org.springframework.security.crypto.bcrypt.BCrypt.gensalt()));
    // TOTP secret
    KeyGenerator keyGen = KeyGenerator.getInstance(TimeBasedOneTimePasswordGenerator.TOTP_ALGORITHM_HMAC_SHA256);
    keyGen.init(256);
    Key key = keyGen.generateKey();
    String secretB32 = org.apache.commons.codec.binary.Base32.encodeBase32String(key.getEncoded()).replace("=", "");
    u.setTotpSecret(secretB32);
    users.save(u);

    String issuer = "PWM";
    String otpauth = "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA256&digits=6&period=30"
      .formatted(issuer, u.getEmail(), secretB32, issuer);

    BitMatrix matrix = new MultiFormatWriter().encode(otpauth, BarcodeFormat.QR_CODE, 220, 220);
    BufferedImage img = MatrixToImageWriter.toBufferedImage(matrix);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    javax.imageio.ImageIO.write(img, "png", baos);
    String dataUrl = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(baos.toByteArray());

    return new RegisterRes(otpauth, dataUrl);
  }

  public record LoginReq(String email, String password) {}
  public record LoginRes(boolean requiresTotp, String tmpToken) {}

  @PostMapping("/auth/login")
  public ResponseEntity<?> login(@RequestBody LoginReq req){
    var u = users.findByEmail(req.email().toLowerCase()).orElse(null);
    if (u == null || !org.springframework.security.crypto.bcrypt.BCrypt.checkpw(req.password(), u.getPasswordHash()))
      return ResponseEntity.status(401).body("Invalid credentials");
    String tmp = jwt.create(Map.of("uid", u.getId().toString(), "stage", "tmp", "exp", (System.currentTimeMillis()/1000)+300));
    return ResponseEntity.ok(new LoginRes(true, tmp));
  }

  public record TotpReq(String tmpToken, String code) {}
  public record TotpRes(String token) {}

  @PostMapping("/auth/totp-verify")
  public ResponseEntity<?> totp(@RequestBody TotpReq req) throws Exception {
    var parsed = jwt.verify(req.tmpToken());
    if (!"tmp".equals(parsed.getBody().get("stage"))) return ResponseEntity.status(401).body("Invalid stage");
    UUID uid = UUID.fromString(parsed.getBody().get("uid", String.class));
    var u = users.findById(uid).orElseThrow();
    var totp = new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 6, TimeBasedOneTimePasswordGenerator.TOTP_ALGORITHM_HMAC_SHA256);
    byte[] secretBytes = new org.apache.commons.codec.binary.Base32().decode(u.getTotpSecret());
    var key = new javax.crypto.spec.SecretKeySpec(secretBytes, "HmacSHA256");
    int now = totp.generateOneTimePassword(key, new Date());
    if (!String.format("%06d", now).equals(req.code())) return ResponseEntity.status(401).body("Invalid code");
    String token = jwt.create(Map.of("uid", u.getId().toString(), "exp", (System.currentTimeMillis()/1000)+3600));
    return ResponseEntity.ok(new TotpRes(token));
  }

  private UserAccount userFromAuth(String auth){
    if (auth == null || !auth.startsWith("Bearer ")) throw new RuntimeException("Missing token");
    var j = jwt.verify(auth.substring(7));
    UUID uid = UUID.fromString(j.getBody().get("uid", String.class));
    return users.findById(uid).orElseThrow();
  }

  public record VaultCreate(String title, String username, String password, String url, String notes) {}
  public record VaultView(UUID id, String title, String username, String password, String url, String notes) {}

  @GetMapping("/vault")
  public java.util.List<VaultView> list(@RequestHeader("Authorization") String auth){
    var u = userFromAuth(auth);
    return items.findByUserOrderByCreatedAtDesc(u).stream().map(v -> new VaultView(
      v.getId(), v.getTitle(), v.getUsername(), crypto.decrypt(v.getPasswordEnc()), v.getUrl(), crypto.decrypt(v.getNotesEnc())
    )).toList();
  }

  @PostMapping("/vault")
  public VaultView create(@RequestHeader("Authorization") String auth, @RequestBody VaultCreate body){
    var u = userFromAuth(auth);
    var v = new VaultItem();
    v.setUser(u); v.setTitle(body.title()); v.setUsername(body.username()); v.setUrl(body.url());
    v.setPasswordEnc(crypto.encrypt(body.password())); v.setNotesEnc(crypto.encrypt(body.notes()));
    v = items.save(v);
    return new VaultView(v.getId(), v.getTitle(), v.getUsername(), body.password(), v.getUrl(), body.notes());
  }

  @DeleteMapping("/vault/{id}")
  public ResponseEntity<?> delete(@RequestHeader("Authorization") String auth, @PathVariable java.util.UUID id){
    var u = userFromAuth(auth);
    var v = items.findById(id).orElse(null);
    if (v == null || !v.getUser().getId().equals(u.getId())) return ResponseEntity.notFound().build();
    items.delete(v); return ResponseEntity.noContent().build();
  }
}
