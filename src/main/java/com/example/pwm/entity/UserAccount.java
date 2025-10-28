// src/main/java/com/example/pwm/entity/UserAccount.java
package com.example.pwm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "ux_users_email", columnList = "email", unique = true)
})
public class UserAccount {

    // --- Getter/Setter ---
    @Setter
    @Getter
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Setter
    @Getter
    @Column(nullable = false, unique = true, length = 320)
    private String email;

    // Standardwert leer + DB-Default (für Schema-Generierung)
    @Getter
    @Setter
    @Column(name = "password_hash", nullable = false, length = 255)
    @ColumnDefault("''")
    private String passwordHash = "";

    @Getter
    @Setter
    @Column(name = "totp_secret_enc", nullable = false, length = 512)
    @ColumnDefault("''")
    private String totpSecretEnc = "";

    @Setter
    @Column(name = "totp_verified", nullable = false)
    private boolean totpVerified = false;

    @Getter
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;


    // Getter/Setter:
    @Setter
    @Getter
    @Column(length = 255)
    private String alexaUserId; // vom Skill geliefert

    @Setter
    @Getter
    @Column(length = 120)
    private String voicePinHash; // BCrypt-Hash (4-8 stellig empfohlen)

    @Setter
    @Getter
    @Column
    private java.time.Instant voiceLockUntil;

    @Setter
    @Getter
    @Column(nullable = false)
    private int voiceFailedAttempts = 0;


    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        // Falls irgendwo doch null reinkommt:
        if (passwordHash == null) passwordHash = "";
        if (totpSecretEnc == null) totpSecretEnc = "";
        if (alexaUserId == null) alexaUserId = "";
        if (voicePinHash == null) voicePinHash = "";
    }

    @PreUpdate
    void preUpdate() {
        if (passwordHash == null) passwordHash = "";
        if (totpSecretEnc == null) totpSecretEnc = "";
    }

    public boolean getTotpVerified() { return totpVerified; }

}
