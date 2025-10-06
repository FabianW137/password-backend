package com.example.pwm.entity;

import jakarta.persistence.*;
import lombok.Setter;

import java.util.Objects;

@Entity
@Table(
        name = "vault_item",
        indexes = {
                @Index(name = "idx_vaultitem_owner", columnList = "owner_id")
        }
)
public class VaultItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // verschlüsselte Felder
    @Setter
    @Column(name = "title_enc", nullable = false, length = 2048)
    private String titleEnc;

    @Setter
    @Column(name = "username_enc", nullable = false, length = 2048)
    private String usernameEnc;

    @Setter
    @Column(name = "password_enc", nullable = false, length = 4096)
    private String passwordEnc;

    @Setter
    @Column(name = "url_enc", length = 2048)
    private String urlEnc;

    @Setter
    @Column(name = "notes_enc", length = 8192)
    private String notesEnc;

    // Besitzer/FK -> users.id
    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "owner_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_vaultitem_owner")
    )
    private UserAccount owner;

    // --- Konstruktoren ---

    public VaultItem() {
        // JPA benötigt einen No-Args-Konstruktor
    }

    public VaultItem(
            UserAccount owner,
            String titleEnc,
            String usernameEnc,
            String passwordEnc,
            String urlEnc,
            String notesEnc
    ) {
        this.owner = owner;
        this.titleEnc = titleEnc;
        this.usernameEnc = usernameEnc;
        this.passwordEnc = passwordEnc;
        this.urlEnc = urlEnc;
        this.notesEnc = notesEnc;
    }

    // --- Getter/Setter ---

    public Long getId() {
        return id;
    }

    public String getTitleEnc() {
        return titleEnc;
    }

    public String getUsernameEnc() {
        return usernameEnc;
    }

    public String getPasswordEnc() {
        return passwordEnc;
    }

    public String getUrlEnc() {
        return urlEnc;
    }

    public String getNotesEnc() {
        return notesEnc;
    }

    public UserAccount getOwner() {
        return owner;
    }

    // --- equals/hashCode nur über id ---

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

    // optional für Logging/Debugging (ohne sensible Daten)
    @Override
    public String toString() {
        return "VaultItem{id=" + id + ", ownerId=" + (owner != null ? owner.getId() : null) + "}";
    }
}
