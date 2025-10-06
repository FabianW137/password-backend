package com.example.pwm.controller;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.example.pwm.entity.UserAccount;
import com.example.pwm.repo.UserAccountRepository;
import com.example.pwm.service.CryptoService;
import com.example.pwm.service.JwtService;
import org.apache.commons.codec.binary.Base32;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final CryptoService crypto;
    private final TimeBasedOneTimePasswordGenerator totp;
    public AuthController(UserAccountRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users; this.encoder = encoder; this.jwt = jwt;
        this.crypto = new CryptoService();
        try { this.totp = new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30)); }
        catch(Exception e){ throw new RuntimeException(e); }
    }
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String,String> body) throws Exception {
        String email = body.get("email"); String password = body.get("password");
        if (email == null || password == null) return ResponseEntity.badRequest().body(Map.of("error","email/password required"));
        if (users.findByEmail(email).isPresent()) return ResponseEntity.status(409).body(Map.of("error","email exists"));
        var u = new UserAccount();
        u.setEmail(email); u.setPasswordHash(encoder.encode(password));
        KeyGenerator keyGen = KeyGenerator.getInstance(totp.getAlgorithm()); keyGen.init(160); SecretKey secretKey = keyGen.generateKey();
        String base32 = new Base32().encodeToString(secretKey.getEncoded());
        u.setTotpSecretEnc(crypto.encrypt(base32));
        users.save(u);
        String label = URLEncoder.encode(email, StandardCharsets.UTF_8);
        String issuer = URLEncoder.encode("PWM", StandardCharsets.UTF_8);
        String otpauth = "otpauth://totp/" + issuer + ":" + label + "?secret=" + base32 + "&issuer=" + issuer + "&digits=" + totp.getPasswordLength() + "&period=30";
        return ResponseEntity.ok(Map.of("message","registered","otpauthUrl",otpauth,"secretBase32",base32));
    }
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String,String> body) {
        String email = body.get("email"); String password = body.get("password");
        Optional<UserAccount> opt = users.findByEmail(email);
        if (opt.isEmpty() || !encoder.matches(password, opt.get().getPasswordHash())) return ResponseEntity.status(401).body(Map.of("error","invalid credentials"));
        var u = opt.get();
        String tmp = jwt.generateTmpToken(u.getId());
        return ResponseEntity.ok(Map.of("tmpToken", tmp));
    }
    @PostMapping("/totp-verify")
    public ResponseEntity<?> verify(@RequestBody Map<String,String> body) throws Exception {
        String tmpToken = body.get("tmpToken"); String code = body.get("code");
        if (tmpToken == null || code == null) return ResponseEntity.badRequest().body(Map.of("error","tmpToken/code required"));
        var claims = jwt.parse(tmpToken);
        if (!"tmp".equals(claims.get("type"))) return ResponseEntity.status(401).body(Map.of("error","invalid tmp token"));
        Long userId = Long.valueOf(claims.getSubject());
        var u = users.findById(userId).orElseThrow();
        String base32 = new CryptoService().decrypt(u.getTotpSecretEnc());
        byte[] keyBytes = new Base32().decode(base32);
        javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, totp.getAlgorithm());
        int expected = totp.generateOneTimePassword(secretKey, java.time.Instant.now());
        if (!code.equals(String.format("%0" + totp.getPasswordLength() + "d", expected))) return ResponseEntity.status(401).body(Map.of("error","invalid code"));
        String token = jwt.generateAccessToken(u.getId(), u.getEmail());
        return ResponseEntity.ok(Map.of("token", token));
    }
}
