package com.example.pwm.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final Algorithm alg;

    public JwtService(@Value("${app.jwt.secret:}") String secretFromProps) {
        String secret = (secretFromProps != null && !secretFromProps.isBlank())
                ? secretFromProps
                : System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET fehlt");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET ist zu kurz (>=32 Zeichen benötigt)");
        }
        this.alg = Algorithm.HMAC256(secret);
    }

    public String issueToken(UUID userId, Duration ttl) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject(userId.toString())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plus(ttl)))
                .sign(alg);
    }

    public String issueTmpToken(UUID userId, Duration ttl) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject("tmp:" + userId)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plus(ttl)))
                .sign(alg);
    }

    public UUID parseUserId(String token) {
        DecodedJWT jwt = JWT.require(alg).build().verify(token);
        String sub = jwt.getSubject();
        if (sub.startsWith("tmp:")) sub = sub.substring(4);
        return UUID.fromString(sub);
    }

    public String parseSubject(String token) {
        return JWT.require(alg).build().verify(token).getSubject();
    }

    /**
     * Prüft Signatur/Ablauf und liefert die Benutzer-UUID aus dem Subject.
     * Akzeptiert auch Subjects der Form "tmp:<uuid>" und schneidet "tmp:" ab.
     * Wirft eine IllegalArgumentException, wenn kein/ungültiges Subject vorliegt.
     */
    public UUID requireUid(String token) {
        DecodedJWT verified = JWT.require(alg).build().verify(token); // verifiziert Signatur & Ablauf
        String sub = verified.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException("JWT hat kein Subject");
        }
        if (sub.startsWith("tmp:")) {
            sub = sub.substring(4);
        }
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("JWT-Subject ist keine gültige UUID: " + sub, ex);
        }
    }
}
