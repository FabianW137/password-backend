package com.example.pwm.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false, unique=true)
    private String email;
    @Column(nullable=false)
    private String passwordHash;
    @Column(length=512)
    private String totpSecretEnc;
    private Instant createdAt = Instant.now();
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getTotpSecretEnc() { return totpSecretEnc; }
    public void setTotpSecretEnc(String totpSecretEnc) { this.totpSecretEnc = totpSecretEnc; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
