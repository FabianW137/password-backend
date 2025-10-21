package com.example.pwm.controller;

import com.example.pwm.entity.UserAccount;
import com.example.pwm.repo.UserAccountRepository;
import com.example.pwm.service.CryptoService;
import com.example.pwm.service.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final CryptoService crypto;
    private final SecureRandom rnd = new SecureRandom();

    public AuthController(UserAccountRepository users,
                          PasswordEncoder encoder,
                          JwtService jwt,
                          CryptoService crypto) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.crypto = crypto;
    }

    /* -------------------- DTOs -------------------- */

    public record RegisterReq(String email, String password) {}
    public record LoginReq(String email, String password) {}
    public record TmpVerifyReq(String tmpToken, String code) {}

    /* -------------------- Helpers -------------------- */

    private static boolean looksLikeEmail(String v) {
        return v != null && v.matches("(?i)^\\S+@\\S+\\.\\S+$");
    }

    /** 20 zufällige Bytes → Base32 (RFC4648, ohne Padding) */
    private String newTotpSecretBase32() {
        byte[] raw = new byte[20];
        rnd.nextBytes(raw);
        return base32Encode(raw);
    }

    /** otpauth:// URI für die Provisionierung (Google Authenticator kompatibel). */
    private String buildOtpUri(String issuer, String accountEmail, String base32Secret) {
        // Label: issuer:account – URL-encoden!
        String label = url(issuer) + ":" + url(accountEmail);
        return "otpauth://totp/" + label +
                "?secret=" + base32Secret +
                "&issuer=" + url(issuer) +
                "&digits=6&period=30&algorithm=SHA1";
    }

    private static String url(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /* -------------------- TOTP (HMAC-SHA1) -------------------- */

    @SuppressWarnings("SameParameterValue")
    private static String base32Encode(byte[] data) {
        final char[] ALPH = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0, bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int idx = (buffer >> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                out.append(ALPH[idx]);
            }
        }
        if (bitsLeft > 0) {
            int idx = (buffer << (5 - bitsLeft)) & 0x1F;
            out.append(ALPH[idx]);
        }
        return out.toString();
    }

    private static byte[] base32Decode(String s) {
        if (s == null) return new byte[0];
        int[] map = new int[256];
        Arrays.fill(map, -1);
        char[] ALPH = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        for (int i = 0; i < ALPH.length; i++) map[ALPH[i]] = i;
        String up = s.trim().toUpperCase(Locale.ROOT).replace("=", "");
        int buffer = 0, bitsLeft = 0;
        ByteBuffer out = ByteBuffer.allocate((up.length() * 5) / 8 + 1);
        for (int i = 0; i < up.length(); i++) {
            int v = up.charAt(i) < 256 ? map[up.charAt(i)] : -1;
            if (v < 0) throw new IllegalArgumentException("Invalid base32 char: " + up.charAt(i));
            buffer = (buffer << 5) | v;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.put((byte) ((buffer >> (bitsLeft - 8)) & 0xFF));
                bitsLeft -= 8;
            }
        }
        out.flip();
        byte[] arr = new byte[out.remaining()];
        out.get(arr);
        return arr;
    }

    /** Berechnet 6-stelligen TOTP-Code (SHA1, 30s). */
    private static int totpNow(byte[] key, long unixTimeSeconds) {
        long step = unixTimeSeconds / 30L;
        byte[] msg = ByteBuffer.allocate(8).putLong(step).array();
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(msg);
            int offset = h[h.length - 1] & 0x0F;
            int bin =
                    ((h[offset] & 0x7F) << 24) |
                            ((h[offset + 1] & 0xFF) << 16) |
                            ((h[offset + 2] & 0xFF) << 8) |
                            (h[offset + 3] & 0xFF);
            return bin % 1_000_000;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean totpMatches(byte[] key, String code) {
        if (code == null || !code.matches("^\\d{6}$")) return false;
        int want = Integer.parseInt(code);
        long now = Instant.now().getEpochSecond();
        // Fenster: -1 .. +1 Schritt
        for (int w = -1; w <= 1; w++) {
            int calc = totpNow(key, now + (w * 30L));
            if (calc == want) return true;
        }
        return false;
    }

    /* -------------------- Endpoints -------------------- */

    /** Registrierung: erzeugt TOTP-Secret, speichert verschlüsselt. */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterReq req) {
        if (req == null || !looksLikeEmail(req.email()) || req.password() == null || req.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email und password sind erforderlich"));
        }
        String email = req.email().trim().toLowerCase(Locale.ROOT);

        if (users.existsByEmailIgnoreCase(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "E-Mail bereits vergeben"));
        }

        String totpSecret = newTotpSecretBase32();
        String totpUri = buildOtpUri("PasswortManager", email, totpSecret);

        UserAccount u = new UserAccount();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setTotpSecretEnc(crypto.encrypt(totpSecret));     // << verschlüsselt ablegen
        u.setTotpVerified(false);

        users.save(u);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", u.getId());
        body.put("email", u.getEmail());
        body.put("totpProvisioningUri", totpUri);
        body.put("totpSecret", totpSecret);

        return ResponseEntity.created(URI.create("/api/auth/users/" + u.getId())).body(body);
    }

    /** Schritt 1: Login → tmpToken (5 Minuten). */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginReq req) {
        if (req == null || req.email() == null || req.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "email und password sind erforderlich"));
        }
        String email = req.email().trim();
        UserAccount u = users.findByEmail(email).orElse(null);
        if (u == null || !encoder.matches(req.password(), u.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Bad credentials"));
        }
        String tmp = jwt.issueTmpToken(u.getId(), Duration.ofMinutes(5));
        return ResponseEntity.ok(Map.of("tmpToken", tmp, "userId", u.getId(), "email", u.getEmail()));
    }

    /** Schritt 2: TOTP verifizieren → endgültiges JWT (12h). */
    @PostMapping("/totp-verify")
    public ResponseEntity<?> verify(@RequestBody TmpVerifyReq req) {
        if (req == null || req.tmpToken() == null || req.code() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "tmpToken und code sind erforderlich"));
        }
        // tmp:<uuid> prüfen & User laden
        UUID uid = jwt.requireUid(req.tmpToken()); // akzeptiert auch tmp:…
        UserAccount u = users.findById(uid).orElse(null);
        if (u == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Bad credentials"));

        String secretB32 = crypto.decrypt(u.getTotpSecretEnc());
        byte[] key = base32Decode(secretB32);

        if (!totpMatches(key, req.code())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "TOTP ungültig"));
        }

        // optional als verifiziert markieren
        if (!Boolean.TRUE.equals(u.getTotpVerified())) {
            u.setTotpVerified(true);
            users.save(u);
        }

        String token = jwt.issueToken(u.getId(), Duration.ofHours(12));
        return ResponseEntity.ok(Map.of("token", token));
    }

    /** Einfacher Healthcheck für das Frontend. */
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true, "ts", Instant.now().toString());
    }
}
