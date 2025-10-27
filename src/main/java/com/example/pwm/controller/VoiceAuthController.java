package com.example.pwm.controller;

import com.example.pwm.service.JwtService;
import com.example.pwm.service.VoiceAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class VoiceAuthController {

    private final VoiceAuthService voice;
    private final JwtService jwt;

    public VoiceAuthController(VoiceAuthService voice, JwtService jwt) {
        this.voice = voice;
        this.jwt = jwt;
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
}
