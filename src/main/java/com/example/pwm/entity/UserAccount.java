// src/main/java/com/example/pwm/entity/UserAccount.java
package com.example.pwm.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "ux_users_email", columnList = "email", unique = true)
})
public class UserAccount {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    // Standardwert leer + DB-Default (für Schema-Generierung)
    @Column(name = "password_hash", nullable = false, length = 255)
    @ColumnDefault("''")
    private String passwordHash = "";

    @Column(name = "totp_secret_enc", nullable = false, length = 512)
    @ColumnDefault("''")
    private String totpSecretEnc = "";

    @Column(name = "totp_verified", nullable = false)
    private boolean totpVerified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        // Falls irgendwo doch null reinkommt:
        if (passwordHash == null) passwordHash = "";
        if (totpSecretEnc == null) totpSecretEnc = "";
    }

    @PreUpdate
    void preUpdate() {
        if (passwordHash == null) passwordHash = "";
        if (totpSecretEnc == null) totpSecretEnc = "";
    }

    // --- Getter/Setter ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getTotpSecretEnc() { return totpSecretEnc; }
    public void setTotpSecretEnc(String totpSecretEnc) { this.totpSecretEnc = totpSecretEnc; }

    public boolean getTotpVerified() { return totpVerified; }
    public void setTotpVerified(boolean totpVerified) { this.totpVerified = totpVerified; }

    public Instant getCreatedAt() { return createdAt; }
}
