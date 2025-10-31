package com.example.pwm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "voice_challenges", indexes = {
        @Index(name = "ix_voice_challenge_user_code", columnList = "user_id,code")
})
public class VoiceChallenge {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Setter
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Setter
    @Column(length = 6, nullable = false)
    private String code;

    @Setter
    @Column(nullable = false)
    private Instant expiresAt;

    @Setter
    @Column(nullable = false)
    private Instant createdAt;

    @Setter
    private Instant verifiedAt;

    @Setter
    @Column(nullable = false)
    private boolean verified;

    @Setter
    @Column(length = 200)
    private String deviceId; // aus dem Alexa-Request

    @Setter
    @Column(nullable = false)
    private int attempts;

}
