package com.example.pwm.repo;

import com.example.pwm.entity.VoiceChallenge;
import com.example.pwm.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VoiceChallengeRepository extends JpaRepository<VoiceChallenge, UUID> {
    Optional<VoiceChallenge> findFirstByUserAndCodeAndVerifiedFalseAndExpiresAtAfter(UserAccount user, String code, Instant now);
    Optional<VoiceChallenge> findFirstByUserAndVerifiedTrueAndExpiresAtAfter(UserAccount user, Instant now);
    long deleteByExpiresAtBefore(Instant t);
}
