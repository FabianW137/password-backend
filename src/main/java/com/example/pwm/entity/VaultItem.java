package com.example.pwm.entity;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(
        name = "vault_item",
        indexes = { @Index(name = "idx_vaultitem_owner", columnList = "owner_id") }
)
public class VaultItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // verschlüsselte Felder
    @Column(name = "title_enc", nullable = false, length = 2048)
    private String titleEnc;

    @Column(name = "username_enc", nullable = false, length = 2048)
    private String usernameEnc;

    @Column(name = "password_enc", nullable = false, length = 4096)
    private String passwordEnc;

    @Column(name = "url_enc", length = 2048)
    private String urlEnc;

    @Column(name = "notes_enc", length = 8192)
    private String notesEnc;

    // FK -> users.id (uuid)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "owner_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_vaultitem_owner")
    )
    private UserAccount owner;

    public VaultItem() {}

    // Getter/Setter
    public Long getId() { return id; }

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

    public UserAccount getOwner() { return owner; }
    public void setOwner(UserAccount owner) { this.owner = owner; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VaultItem)) return false;
        VaultItem that = (VaultItem) o;
        return id != null && Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return 31; }
}
