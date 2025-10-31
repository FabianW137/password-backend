package com.example.pwm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "voice_link_codes", indexes = {
        @Index(name = "ix_voice_link_code_code", columnList = "code", unique = true)
})
public class VoiceLinkCode {

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

}
