package com.example.pwm.controller;

import com.example.pwm.entity.UserAccount;
import com.example.pwm.repo.UserAccountRepository;
import com.example.pwm.service.JwtService;
import com.example.pwm.service.VoiceAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(VoiceAuthController.class);

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

    @PostMapping("/voice/link/start")
    public Map<String,Object> startLink(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        String code = voice.createLinkCode(userId);
        return Map.of("code", code, "ttlSeconds", 600);
    }

    @PostMapping("/voice/link/complete")
    public Map<String,Object> completeLink(@RequestBody LinkReq req) {
        var exists = users.findByAlexaUserId(req.alexaUserId());
        if (exists.isPresent()) {
            return Map.of("ok", false, "message", "alexa-id-already-linked");
        }
        boolean ok = voice.completeLink(req.code(), req.alexaUserId());
        return ok ? Map.of("ok", true)
                : Map.of("ok", false, "message", "invalid-or-expired");
    }


    @PostMapping("/voice/challenge")
    public Map<String,Object> startChallenge(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        String code = voice.createChallenge(userId);
        return Map.of("code", code, "ttlSeconds", 180);
    }

    @PostMapping("/verify")
    public Map<String,Object> verifyFromAlexa(@RequestBody VerifyReq req) {
        var res = voice.verifyFromAlexa(req.code(), req.pin(), req.alexaUserId(), req.deviceId());
        return res;
    }

    @PostMapping("/voice/finalize")
    public ResponseEntity<?> finalizeVoice(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        if (!voice.hasVerifiedChallenge(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "no-verified-challenge"));
        }
        String token = jwt.issueToken(userId, Duration.ofHours(12));
        voice.deleteAllChallengesOfUser(userId);
        return ResponseEntity.ok(Map.of("token", token));
    }

    public record LinkReq(String code, String alexaUserId) {}
    public record VerifyReq(String code, String pin, String alexaUserId, String deviceId) {}

    public record PinReq(String pin) {}

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
        u.setVoiceFailedAttempts(0);
        u.setVoiceLockUntil(null);
        users.save(u);
        return ResponseEntity.ok(Map.of("ok", true));
    }

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
