package com.example.pwm.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="users")
public class UserAccount {
  @Id @GeneratedValue private UUID id;
  @Column(unique=true, nullable=false) private String email;
  @Column(nullable=false) private String passwordHash;
  @Column(nullable=false) private String totpSecret;
  @Column(nullable=false) private Instant createdAt = Instant.now();

  public UUID getId(){return id;} public void setId(UUID id){this.id=id;}
  public String getEmail(){return email;} public void setEmail(String e){this.email=e;}
  public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String p){this.passwordHash=p;}
  public String getTotpSecret(){return totpSecret;} public void setTotpSecret(String t){this.totpSecret=t;}
  public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant t){this.createdAt=t;}
}
