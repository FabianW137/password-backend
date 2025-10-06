package com.example.pwm.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final Algorithm alg;

    public JwtService() {
        var secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET fehlt");
        }
        this.alg = Algorithm.HMAC256(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Standard-Token (z. B. 12h gültig) */
    public String issueToken(long userId, Duration ttl) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject(Long.toString(userId))
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plus(ttl)))
                .sign(alg);
    }

    /** Kurzlebiger Token (z. B. 5 min für TOTP) */
    public String issueTmpToken(long userId, Duration ttl) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject("tmp:" + userId)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plus(ttl)))
                .sign(alg);
    }

    /** Wirft bei ungültig/expired. */
    public long parseUserId(String token) {
        DecodedJWT jwt = JWT.require(alg).build().verify(token);
        return Long.parseLong(jwt.getSubject());
    }

    public String parseSubject(String token) {
        return JWT.require(alg).build().verify(token).getSubject();
    }
}
