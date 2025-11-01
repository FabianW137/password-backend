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
import java.net.URLEncoder;
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

   
    private static final int FAILS_PER_TIER = 3;
    private static final Duration BASE_LOCK = Duration.ofMinutes(5);
    private static final Duration MAX_LOCK = Duration.ofHours(24);

    public AuthController(UserAccountRepository users,
                          PasswordEncoder encoder,
                          JwtService jwt,
                          CryptoService crypto) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.crypto = crypto;
    }


    public record RegisterReq(String email, String password) {}
    public record LoginReq(String email, String password) {}
    public record TmpVerifyReq(String tmpToken, String code) {}


    private static boolean looksLikeEmail(String v) {
        return v != null && v.matches("(?i)^\\S+@\\S+\\.\\S+$");
    }

    private String newTotpSecretBase32() {
        byte[] raw = new byte[20];
        rnd.nextBytes(raw);
        return base32Encode(raw);
    }

    private String buildOtpUri(String issuer, String accountEmail, String base32Secret) {
        String label = url(issuer) + ":" + url(accountEmail);
        return "otpauth://totp/" + label +
                "?secret=" + base32Secret +
                "&issuer=" + url(issuer) +
                "&digits=6&period=30&algorithm=SHA1";
    }

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String base32Encode(byte[] data) {
        if (data == null || data.length == 0) return "";
        final char[] ALPH = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0, bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(ALPH[(buffer >> (bitsLeft - 5)) & 31]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            out.append(ALPH[(buffer << (5 - bitsLeft)) & 31]);
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

    private static int hotp(byte[] key, long counter) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA1"));
            byte[] msg = ByteBuffer.allocate(8).putLong(counter).array();
            byte[] h = mac.doFinal(msg);
            int off = h[h.length - 1] & 0x0F;
            int bin = ((h[off] & 0x7F) << 24) | ((h[off + 1] & 0xFF) << 16) |
                      ((h[off + 2] & 0xFF) << 8) | (h[off + 3] & 0xFF);
            return bin % 1_000_000;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int totp(byte[] key, long ts, long stepSeconds) {
        long counter = Math.floorDiv(ts, stepSeconds);
        return hotp(key, counter);
    }

    private static boolean totpMatches(byte[] key, int code, long ts, long stepSeconds, int window) {
        for (int w = -window; w <= window; w++) {
            if (totp(key, ts + w * stepSeconds, stepSeconds) == code) return true;
        }
        return false;
    }


    private static Duration lockDurationForTier(int tier) {
        if (tier <= 1) return BASE_LOCK;
        Duration d = BASE_LOCK.multipliedBy(1L << (tier - 1));
        if (d.compareTo(MAX_LOCK) > 0) return MAX_LOCK;
        return d;
    }

    private static long secondsUntil(Instant ts) {
        long s = Duration.between(Instant.now(), ts).getSeconds();
        return Math.max(0, s);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterReq req) {
        if (req == null || req.email() == null || req.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "email und password sind erforderlich"));
        }
        String email = req.email().trim();
        if (!looksLikeEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("error", "ungültige E-Mail"));
        }
        if (users.existsByEmailIgnoreCase(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "E-Mail bereits registriert"));
        }

        String secretB32 = newTotpSecretBase32();
        UserAccount u = new UserAccount();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setTotpSecretEnc(crypto.encrypt(secretB32));
        u.setTotpVerified(false);
        u.setVoiceFailedAttempts(0);
        u.setVoiceLockUntil(null);
        users.save(u);

        String uri = buildOtpUri("PWM", email, secretB32);
        return ResponseEntity.created(URI.create("/api/auth/register"))
                .body(Map.of("otpauthUrl", uri, "secretBase32", secretB32));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginReq req) {
        if (req == null || req.email() == null || req.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "email und password sind erforderlich"));
        }
        String email = req.email().trim();
        String pw = req.password();

        UserAccount u = users.findByEmail(email).orElse(null);

        if (u != null && u.getVoiceLockUntil() != null) {
            Instant lockUntil = u.getVoiceLockUntil();
            if (lockUntil.isAfter(Instant.now())) {
                long left = secondsUntil(lockUntil);
                return ResponseEntity.status(429) 
                        .header("Retry-After", String.valueOf(left))
                        .body(Map.of(
                                "error", "locked",
                                "message", "Zu viele Fehlversuche. Bitte später erneut versuchen.",
                                "retryAfterSeconds", left
                        ));
            }
        }

        if (u == null || !encoder.matches(pw, u.getPasswordHash())) {
            if (u != null) {
                int fails = (u.getVoiceFailedAttempts() == 0 ? 0 : u.getVoiceFailedAttempts()) + 1;
                u.setVoiceFailedAttempts(fails);

                if (fails % FAILS_PER_TIER == 0) {
                    int tier = fails / FAILS_PER_TIER;         
                    Duration lock = lockDurationForTier(tier);    
                    u.setVoiceLockUntil(Instant.now().plus(lock));
                }
                users.save(u);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Bad credentials"));
        }

        u.setVoiceFailedAttempts(0);
        u.setVoiceLockUntil(null);
        users.save(u);

        String tmp = jwt.issueTmpToken(u.getId(), Duration.ofMinutes(5));
        return ResponseEntity.ok(Map.of("tmpToken", tmp, "userId", u.getId(), "email", u.getEmail()));
    }

    @PostMapping("/totp-verify")
    public ResponseEntity<?> verify(@RequestBody TmpVerifyReq req) {
        if (req == null || req.tmpToken() == null || req.code() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "tmpToken und code sind erforderlich"));
        }

        UUID uid;
        try {
            uid = jwt.requireUid(req.tmpToken()); 
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Bad credentials"));
        }

        UserAccount u = users.findById(uid).orElse(null);
        if (u == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Bad credentials"));
        }

        String secretB32 = crypto.decrypt(u.getTotpSecretEnc());
        byte[] key = base32Decode(secretB32);
        int provided;
        try {
            provided = Integer.parseInt(req.code().replaceAll("\\D+", ""));
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Bad credentials"));
        }
        boolean ok = totpMatches(key, provided, Instant.now().getEpochSecond(), 30, 1);
        if (!ok) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Bad credentials"));
        }

        u.setTotpVerified(true);
        users.save(u);

        String token = jwt.issueToken(u.getId(), Duration.ofHours(12));
        boolean alexaLinked = u.getAlexaUserId() != null && !u.getAlexaUserId().isBlank();
        boolean voicePinSet  = u.getVoicePinHash() != null && !u.getVoicePinHash().isBlank();
        return ResponseEntity.ok(Map.of(
                "token", token,
                "email", u.getEmail(),
                "alexaLinked", alexaLinked,
                "voicePinSet", voicePinSet
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(org.springframework.security.core.Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
        }
        java.util.UUID userId = (java.util.UUID) auth.getPrincipal();
        var u = users.findById(userId).orElseThrow();

        boolean alexaLinked = u.getAlexaUserId() != null && !u.getAlexaUserId().isBlank();
        boolean voicePinSet = u.getVoicePinHash() != null && !u.getVoicePinHash().isBlank();

        return ResponseEntity.ok(Map.of(
                "email", u.getEmail(),
                "alexaLinked", alexaLinked,
                "voicePinSet", voicePinSet
        ));
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true, "ts", Instant.now().toString());
    }
}

