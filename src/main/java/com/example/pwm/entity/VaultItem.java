package com.example.pwm.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="vault_items")
public class VaultItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional=false)
    private UserAccount owner;
    @Column(length=1024) private String titleEnc;
    @Column(length=4096) private String usernameEnc;
    @Column(length=4096) private String passwordEnc;
    @Column(length=4096) private String urlEnc;
    @Column(length=8192) private String notesEnc;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    @PreUpdate public void onUpdate(){ this.updatedAt = Instant.now(); }
    public Long getId(){return id;}
    public void setId(Long id){this.id=id;}
    public UserAccount getOwner(){return owner;}
    public void setOwner(UserAccount owner){this.owner=owner;}
    public String getTitleEnc(){return titleEnc;}
    public void setTitleEnc(String v){this.titleEnc=v;}
    public String getUsernameEnc(){return usernameEnc;}
    public void setUsernameEnc(String v){this.usernameEnc=v;}
    public String getPasswordEnc(){return passwordEnc;}
    public void setPasswordEnc(String v){this.passwordEnc=v;}
    public String getUrlEnc(){return urlEnc;}
    public void setUrlEnc(String v){this.urlEnc=v;}
    public String getNotesEnc(){return notesEnc;}
    public void setNotesEnc(String v){this.notesEnc=v;}
    public Instant getCreatedAt(){return createdAt;}
    public void setCreatedAt(Instant t){this.createdAt=t;}
    public Instant getUpdatedAt(){return updatedAt;}
    public void setUpdatedAt(Instant t){this.updatedAt=t;}
}
