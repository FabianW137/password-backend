package com.example.pwm.controller;

import com.example.pwm.entity.UserAccount;
import com.example.pwm.repo.UserAccountRepository;
import com.example.pwm.service.JwtService;
import com.example.pwm.service.VoiceAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class VoiceAuthController {

    private final VoiceAuthService voice;
    private final JwtService jwt;
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;

    public VoiceAuthController(VoiceAuthService voice, JwtService jwt, UserAccountRepository users, PasswordEncoder encoder) {
        this.voice = voice;
        this.jwt = jwt;
        this.users = users;
        this.encoder = encoder;
    }

    // 1) Link-Code erzeugen (User ist normal eingeloggt – finaler Token)
    @PostMapping("/voice/link/start")
    public Map<String,Object> startLink(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        String code = voice.createLinkCode(userId);
        return Map.of("code", code, "ttlSeconds", 600);
    }

    // 2) Alexa ruft dies auf, um die Verknüpfung herzustellen
    @PostMapping("/voice/link/complete")
    public Map<String,Object> completeLink(@RequestBody LinkReq req) {
        boolean ok = voice.completeLink(req.code(), req.alexaUserId());
        return Map.of("ok", ok);
    }

    // 3) Challenge für Voice-MFA erzeugen (User ist mit TMP-Token authentifiziert – aber unser Filter akzeptiert beide)
    @PostMapping("/voice/challenge")
    public Map<String,Object> startChallenge(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        String code = voice.createChallenge(userId);
        return Map.of("code", code, "ttlSeconds", 180);
    }

    // 4) Alexa-Skill Verifikation (der Skill-Code postet gegen /api/verify)
    @PostMapping("/verify")
    public Map<String,Object> verifyFromAlexa(@RequestBody VerifyReq req) {
        var res = voice.verifyFromAlexa(req.code(), req.pin(), req.alexaUserId(), req.deviceId());
        return res;
    }

    // 5) Frontend finalisiert (analog zu TOTP: tmp → finaler JWT)
    @PostMapping("/voice/finalize")
    public ResponseEntity<?> finalizeVoice(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        if (!voice.hasVerifiedChallenge(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "no-verified-challenge"));
        }
        String token = jwt.issueToken(userId, Duration.ofHours(12));
        return ResponseEntity.ok(Map.of("token", token));
    }

    public record LinkReq(String code, String alexaUserId) {}
    public record VerifyReq(String code, String pin, String alexaUserId, String deviceId) {}

    // DTO
    public record PinReq(String pin) {}

    /** Sprach-PIN setzen/ändern (nur wenn Alexa verknüpft ist) */
    @PostMapping("/voice/pin")
    @Transactional
    public ResponseEntity<?> setVoicePin(Authentication auth, @RequestBody PinReq req) {
        UUID userId = (UUID) auth.getPrincipal();
        if (req == null || req.pin() == null || !req.pin().matches("\\d{4,8}")) {
            return ResponseEntity.badRequest().body(Map.of("error", "pin-invalid", "message", "PIN muss 4–8 Ziffern haben."));
        }
        UserAccount u = users.findById(userId).orElseThrow();
        if (u.getAlexaUserId() == null || u.getAlexaUserId().isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "not-linked", "message", "Alexa ist nicht verknüpft."));
        }
        u.setVoicePinHash(encoder.encode(req.pin()));
        // Reset von Fehlversuchen/Lock beim Ändern sinnvoll
        u.setVoiceFailedAttempts(0);
        u.setVoiceLockUntil(null);
        users.save(u);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Sprach-PIN löschen (zurücksetzen) */
    @DeleteMapping("/voice/pin")
    @Transactional
    public ResponseEntity<?> clearVoicePin(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        UserAccount u = users.findById(userId).orElseThrow();
        if (u.getAlexaUserId() == null || u.getAlexaUserId().isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "not-linked"));
        }
        u.setVoicePinHash("");
        u.setVoiceFailedAttempts(0);
        u.setVoiceLockUntil(null);
        users.save(u);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
