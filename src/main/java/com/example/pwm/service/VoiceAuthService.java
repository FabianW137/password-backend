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
        // User anhand alexaUserId
        UserAccount user = users.findAll().stream()
                .filter(u -> alexaUserId != null && alexaUserId.equals(u.getAlexaUserId()))
                .findFirst().orElse(null);
        if (user == null) return Map.of("success", false, "message", "no-link");

        // Lock prüfen
        Instant now = Instant.now();
        if (user.getVoiceLockUntil() != null && user.getVoiceLockUntil().isAfter(now)) {
            return Map.of("success", false, "message", "locked");
        }

        // PIN prüfen
        if (user.getVoicePinHash() == null || user.getVoicePinHash().isBlank()) {
            return Map.of("success", false, "message", "no-pin");
        }
        boolean pinOk = encoder.matches(pin == null ? "" : pin, user.getVoicePinHash());
        if (!pinOk) {
            int fails = user.getVoiceFailedAttempts() + 1;
            user.setVoiceFailedAttempts(fails);
            if (fails >= MAX_ATTEMPTS) {
                user.setVoiceLockUntil(now.plus(LOCK_DURATION));
                user.setVoiceFailedAttempts(0);
            }
            users.save(user);
            return Map.of("success", false, "message", "bad-pin");
        }

        // Challenge suchen
        Optional<VoiceChallenge> optCh = challenges.findFirstByUserAndCodeAndVerifiedFalseAndExpiresAtAfter(user, code, now);
        if (optCh.isEmpty()) {
            return Map.of("success", false, "message", "bad-code");
        }

        VoiceChallenge ch = optCh.get();
        ch.setVerified(true);
        ch.setVerifiedAt(now);
        ch.setDeviceId(deviceId);
        challenges.save(ch);

        // Fehlversuche zurücksetzen
        user.setVoiceFailedAttempts(0);
        users.save(user);

        return Map.of("success", true, "message", "ok");
    }

    @Transactional(readOnly = true)
    public boolean hasVerifiedChallenge(UUID userId) {
        UserAccount u = users.findById(userId).orElseThrow();
        return challenges.findFirstByUserAndVerifiedTrueAndExpiresAtAfter(u, Instant.now()).isPresent();
    }
}
