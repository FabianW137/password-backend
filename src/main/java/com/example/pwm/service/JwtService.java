package com.example.pwm.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public class JwtService {

    private final SecretKey key;

    public JwtService() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET fehlt");
        }
        // Für HS256 muss der Key ausreichend lang sein (>= 256 bit)
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Standard-Token (z. B. 12h gültig) */
    public String issueToken(long userId, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)              // HS256 automatisch
                .compact();
    }

    /** Temporärer Token (z. B. 5min für TOTP-Schritt) */
    public String issueTmpToken(long userId, Duration ttl) {
        // identisch – wenn du willst, kannst du einen Claim "tmp":true setzen
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("tmp:" + userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** Liest die userId aus einem regulären Token (wirft bei Invalidität eine Exception). */
    public long parseUserId(String token) {
        var claims = Jwts.parser()
                .verifyWith(key)           // SecretKey, passt zur 0.12-API
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    /** Prüft nur Signatur und Ablauf (z. B. für tmp-Token), liefert Subject zurück. */
    public String parseSubject(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
