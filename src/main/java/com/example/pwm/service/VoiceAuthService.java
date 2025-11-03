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
        var opt = linkCodes.findByCode(code);
        if (opt.isEmpty()) return false;
        var link = opt.get();
        if (link.getExpiresAt().isBefore(Instant.now())) return false;

        var user = link.getUser();
        if (alexaUserId == null || alexaUserId.isBlank()) return false;

        var existing = users.findByAlexaUserId(alexaUserId);
        if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
            return false; 
        }

        try {
            user.setAlexaUserId(alexaUserId);
            users.save(user);
            linkCodes.delete(link);
            return true;
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            return false;
        }
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
        // --- Neu: Normalisierung ---
        String normCode = code == null ? "" : code.replaceAll("\\D+", ""); // nur Ziffern
        String normPin  = pin  == null ? "" : pin.replaceAll("\\D+", "");  // nur Ziffern

        // User anhand alexaUserId
        UserAccount user = users.findAll().stream()
                .filter(u -> alexaUserId != null && alexaUserId.equals(u.getAlexaUserId()))
                .findFirst().orElse(null);
        if (user == null) return Map.of("success", false, "message", "no-link");

        Instant now = Instant.now();

        // Lock prüfen
        if (user.getVoiceLockUntil() != null && user.getVoiceLockUntil().isAfter(now)) {
            return Map.of("success", false, "message", "locked");
        }

        // PIN prüfen
        if (user.getVoicePinHash() == null || user.getVoicePinHash().isBlank()) {
            return Map.of("success", false, "message", "no-pin");
        }
        boolean pinOk = encoder.matches(normPin, user.getVoicePinHash());
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

        // Challenge suchen (unverifiziert + nicht abgelaufen) – mit normalisiertem Code
        Optional<VoiceChallenge> optCh =
                challenges.findFirstByUserAndCodeAndVerifiedFalseAndExpiresAtAfter(user, normCode, now);
        if (optCh.isEmpty()) {
            return Map.of("success", false, "message", "bad-code");
        }

        // Verifizieren
        VoiceChallenge ch = optCh.get();
        ch.setVerified(true);
        ch.setVerifiedAt(now);
        ch.setDeviceId(deviceId);
        challenges.save(ch);

        // Fehlversuche resetten
        user.setVoiceFailedAttempts(0);
        users.save(user);

        return Map.of("success", true, "message", "ok");
    }


    @Transactional(readOnly = true)
    public boolean hasVerifiedChallenge(UUID userId) {
        UserAccount u = users.findById(userId).orElseThrow();
        return challenges.findFirstByUserAndVerifiedTrueAndExpiresAtAfter(u, Instant.now()).isPresent();
    }
    @Transactional
    public void deleteAllChallengesOfUser(UUID userId) {
        UserAccount u = users.findById(userId).orElseThrow();
        // Minimal-invasiv ohne neue Repo-Methoden:
        challenges.findAll()
                .stream()
                .filter(ch -> ch.getUser().getId().equals(u.getId()))
                .forEach(challenges::delete);
    }

}
