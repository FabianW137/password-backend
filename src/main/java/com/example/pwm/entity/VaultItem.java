package com.example.pwm.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "vault_items", indexes = {
        @Index(name = "ix_vault_owner", columnList = "owner_id"),
        @Index(name = "ix_vault_created_at", columnList = "created_at")
})
public class VaultItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK auf users.id (UUID)
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "owner_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_vaultitem_owner")
    )
    private UserAccount owner;

    @Column(name = "title_enc", nullable = false, length = 1024)
    @ColumnDefault("''")
    private String titleEnc = "";

    @Column(name = "username_enc", nullable = false, length = 1024)
    @ColumnDefault("''")
    private String usernameEnc = "";

    @Column(name = "password_enc", nullable = false, length = 2048)
    @ColumnDefault("''")
    private String passwordEnc = "";

    @Column(name = "url_enc", nullable = false, length = 1024)
    @ColumnDefault("''")
    private String urlEnc = "";

    @Column(name = "notes_enc", nullable = false, columnDefinition = "text")
    @ColumnDefault("''")
    private String notesEnc = "";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
        // Null-Schutz
        if (titleEnc == null) titleEnc = "";
        if (usernameEnc == null) usernameEnc = "";
        if (passwordEnc == null) passwordEnc = "";
        if (urlEnc == null) urlEnc = "";
        if (notesEnc == null) notesEnc = "";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        // Null-Schutz
        if (titleEnc == null) titleEnc = "";
        if (usernameEnc == null) usernameEnc = "";
        if (passwordEnc == null) passwordEnc = "";
        if (urlEnc == null) urlEnc = "";
        if (notesEnc == null) notesEnc = "";
    }

    // --- Getter/Setter ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UserAccount getOwner() { return owner; }
    public void setOwner(UserAccount owner) { this.owner = owner; }

    public String getTitleEnc() { return titleEnc; }
    public void setTitleEnc(String titleEnc) { this.titleEnc = titleEnc; }

    public String getUsernameEnc() { return usernameEnc; }
    public void setUsernameEnc(String usernameEnc) { this.usernameEnc = usernameEnc; }

    public String getPasswordEnc() { return passwordEnc; }
    public void setPasswordEnc(String passwordEnc) { this.passwordEnc = passwordEnc; }

    public String getUrlEnc() { return urlEnc; }
    public void setUrlEnc(String urlEnc) { this.urlEnc = urlEnc; }

    public String getNotesEnc() { return notesEnc; }
    public void setNotesEnc(String notesEnc) { this.notesEnc = notesEnc; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
