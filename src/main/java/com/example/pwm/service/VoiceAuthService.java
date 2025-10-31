package com.example.pwm.service;

import com.example.pwm.entity.UserAccount;
import com.example.pwm.entity.VoiceChallenge;
import com.example.pwm.entity.VoiceLinkCode;
import com.example.pwm.repo.UserAccountRepository;
import com.example.pwm.repo.VoiceChallengeRepository;
import com.example.pwm.repo.VoiceLinkCodeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class VoiceAuthService {

    private final UserAccountRepository users;
    private final VoiceLinkCodeRepository linkCodes;
    private final VoiceChallengeRepository challenges;
    private final PasswordEncoder encoder;
    private final SecureRandom rnd = new SecureRandom();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VoiceAuthService.class);
    // Policy
    private static final Duration LINK_TTL = Duration.ofMinutes(10);
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(3);
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(5);

    public VoiceAuthService(UserAccountRepository users,
                            VoiceLinkCodeRepository linkCodes,
                            VoiceChallengeRepository challenges,
                            PasswordEncoder encoder) {
        this.users = users;
        this.linkCodes = linkCodes;
        this.challenges = challenges;
        this.encoder = encoder;
    }

    private String code6() {
        int x = rnd.nextInt(1_000_000);
        return String.format("%06d", x);
    }

    @Transactional
    public String createLinkCode(UUID userId) {
        UserAccount u = users.findById(userId).orElseThrow();
        // Aufräumen
        linkCodes.deleteByExpiresAtBefore(Instant.now());
        VoiceLinkCode c = new VoiceLinkCode();
        c.setUser(u);
        c.setCode(code6());
        c.setExpiresAt(Instant.now().plus(LINK_TTL));
        linkCodes.save(c);
        return c.getCode();
    }

    @Transactional
    public boolean completeLink(String code, String alexaUserId) {
        Optional<VoiceLinkCode> opt = linkCodes.findByCode(code);
        if (opt.isEmpty()) return false;
        VoiceLinkCode c = opt.get();
        if (c.getExpiresAt().isBefore(Instant.now())) return false;

        UserAccount u = c.getUser();
        u.setAlexaUserId(alexaUserId);
        users.save(u);
        linkCodes.delete(c);
        return true;
    }

    @Transactional
    public String createChallenge(UUID userId) {
        UserAccount u = users.findById(userId).orElseThrow();
        challenges.deleteByExpiresAtBefore(Instant.now());
        VoiceChallenge ch = new VoiceChallenge();
        ch.setUser(u);
        ch.setCode(code6());
        ch.setCreatedAt(Instant.now());
        ch.setExpiresAt(Instant.now().plus(CHALLENGE_TTL));
        ch.setVerified(false);
        ch.setAttempts(0);
        challenges.save(ch);
        return ch.getCode();
    }

    @Transactional
    public Map<String, Object> verifyFromAlexa(String code, String pin, String alexaUserId, String deviceId) {
        // Sensible Werte maskieren
        final String maskedCode = (code == null) ? "null"
                : (code.length() <= 2 ? "**" : "**" + code.substring(code.length() - 2));

        log.debug("voice.verify start alexaUserId={} deviceId={} code={}", alexaUserId, deviceId, maskedCode);

        // User anhand der Alexa-UserId
        UserAccount user = users.findAll().stream()
                .filter(u -> alexaUserId != null && alexaUserId.equals(u.getAlexaUserId()))
                .findFirst().orElse(null);

        if (user == null) {
            log.debug("voice.verify result=no-link alexaUserId={}", alexaUserId);
            return Map.of("success", false, "message", "no-link");
        }

        java.util.UUID uid = user.getId();
        java.time.Instant now = java.time.Instant.now();

        // Lock prüfen
        if (user.getVoiceLockUntil() != null && user.getVoiceLockUntil().isAfter(now)) {
            log.debug("voice.verify result=locked uid={} until={}", uid, user.getVoiceLockUntil());
            return Map.of("success", false, "message", "locked");
        }

        // PIN vorhanden?
        if (user.getVoicePinHash() == null || user.getVoicePinHash().isBlank()) {
            log.debug("voice.verify result=no-pin uid={}", uid);
            return Map.of("success", false, "message", "no-pin");
        }

        // PIN prüfen
        boolean pinOk = encoder.matches(pin == null ? "" : pin, user.getVoicePinHash());
        if (!pinOk) {
            int fails = user.getVoiceFailedAttempts() + 1;
            user.setVoiceFailedAttempts(fails);
            if (fails >= MAX_ATTEMPTS) {
                user.setVoiceLockUntil(now.plus(LOCK_DURATION));
                user.setVoiceFailedAttempts(0);
                log.debug("voice.verify result=bad-pin uid={} fails={} -> lockedUntil={}", uid, fails, user.getVoiceLockUntil());
            } else {
                log.debug("voice.verify result=bad-pin uid={} fails={}", uid, fails);
            }
            users.save(user);
            return Map.of("success", false, "message", "bad-pin");
        }

        // Challenge suchen (richtiges User-Match, unverifiziert, nicht abgelaufen)
        java.util.Optional<VoiceChallenge> optCh =
                challenges.findFirstByUserAndCodeAndVerifiedFalseAndExpiresAtAfter(user, code, now);

        if (optCh.isEmpty()) {
            log.debug("voice.verify result=bad-code uid={} code={}", uid, maskedCode);
            return Map.of("success", false, "message", "bad-code");
        }

        // Challenge verifizieren
        VoiceChallenge ch = optCh.get();
        ch.setVerified(true);
        ch.setVerifiedAt(now);
        ch.setDeviceId(deviceId);
        challenges.save(ch);

        // Fehlversuche zurücksetzen
        user.setVoiceFailedAttempts(0);
        users.save(user);

        log.debug("voice.verify result=ok uid={} challengeId={} deviceId={} expiresAt={}",
                uid, ch.getId(), deviceId, ch.getExpiresAt());

        return Map.of("success", true, "message", "ok");
    }

    @Transactional(readOnly = true)
    public boolean hasVerifiedChallenge(UUID userId) {
        UserAccount u = users.findById(userId).orElseThrow();
        return challenges.findFirstByUserAndVerifiedTrueAndExpiresAtAfter(u, Instant.now()).isPresent();
    }
}
