package com.example.pwm.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "vault_items")
public class VaultItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Long Autoincrement
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false) // verweist auf users.id (UUID)
    private UserAccount owner;

    @Column(name = "title_enc",    nullable = false, columnDefinition = "text")
    private String titleEnc;

    @Column(name = "username_enc", nullable = false, columnDefinition = "text")
    private String usernameEnc;

    @Column(name = "password_enc", nullable = false, columnDefinition = "text")
    private String passwordEnc;

    @Column(name = "url_enc",      nullable = false, columnDefinition = "text")
    private String urlEnc;

    @Column(name = "notes_enc",    nullable = false, columnDefinition = "text")
    private String notesEnc;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /* ---------- Lifecycle ---------- */

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        // Nulls vermeiden (AES/GCM-Decrypt erwartet validen String)
        if (titleEnc    == null) titleEnc = "";
        if (usernameEnc == null) usernameEnc = "";
        if (passwordEnc == null) passwordEnc = "";
        if (urlEnc      == null) urlEnc = "";
        if (notesEnc    == null) notesEnc = "";
    }

    /* ---------- Getter/Setter ---------- */

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

    /* ---------- equals/hashCode nur über id ---------- */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VaultItem that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return 31;
    }
}
