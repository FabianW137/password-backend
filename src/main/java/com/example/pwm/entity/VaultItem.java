package com.example.pwm.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vault_items")
public class VaultItem {
    @Id @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    private UserAccount user;

    private String title;
    private String username;

    @Column(length = 4000)
    private String passwordEnc;

    private String url;

    @Column(length = 4000)
    private String notesEnc;

    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UserAccount getUser() { return user; }
    public void setUser(UserAccount user) { this.user = user; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordEnc() { return passwordEnc; }
    public void setPasswordEnc(String passwordEnc) { this.passwordEnc = passwordEnc; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getNotesEnc() { return notesEnc; }
    public void setNotesEnc(String notesEnc) { this.notesEnc = notesEnc; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
