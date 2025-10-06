package com.example.pwm.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.util.Date;

public class JwtService {
    private final Key key;
    public JwtService() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET fehlt oder ist zu kurz (>=32 Zeichen)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public String generateAccessToken(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60*60*12)))
                .claim("email", email)
                .claim("type", "access")
                .signWith(key)
                .compact();
    }
    public String generateTmpToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .claim("type", "tmp")
                .signWith(key)
                .compact();
    }
    public io.jsonwebtoken.Claims parse(String jwt) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt).getPayload();
    }
}
