package com.example.pwm.repo;

import com.example.pwm.entity.VoiceLinkCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VoiceLinkCodeRepository extends JpaRepository<VoiceLinkCode, UUID> {
    Optional<VoiceLinkCode> findByCode(String code);
    long deleteByExpiresAtBefore(Instant t);
}
