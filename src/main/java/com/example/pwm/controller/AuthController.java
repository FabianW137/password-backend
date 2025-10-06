package com.example.pwm.controller;

import com.example.pwm.entity.UserAccount;
import com.example.pwm.repo.UserAccountRepository;
import com.example.pwm.service.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAccountRepository users;
    private final JwtService jwt;
    private final PasswordEncoder encoder;

    public AuthController(UserAccountRepository users, JwtService jwt, PasswordEncoder encoder) {
        this.users = users;
        this.jwt = jwt;
        this.encoder = encoder;
    }

    // -------- DTOs --------
    public record LoginReq(String email, String password) {}
    public record RegisterReq(String email, String password) {}

    /**
     * Registrierung: Passwort wird gehasht gespeichert.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterReq req) {
        if (req == null || req.email() == null || req.password() == null
                || req.email().isBlank() || req.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email und password sind erforderlich"));
        }
        if (users.existsByEmail(req.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "E-Mail bereits registriert"));
        }

        String hash = encoder.encode(req.password());
        UserAccount u = new UserAccount(req.email(), hash);
        users.save(u);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", u.getId(), "email", u.getEmail()));
    }

    /**
     * Login: Prüft das Passwort mit PasswordEncoder#matches und gibt einen temporären Token zurück
     * (z.B. für anschließende TOTP-Verifikation).
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginReq req) {
        UserAccount u = users.findByEmail(req.email())
                .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new IllegalArgumentException("Bad credentials"));

        String tmp = jwt.issueTmpToken(u.getId(), Duration.ofMinutes(5));
        return Map.of("tmpToken", tmp);
    }

    /**
     * Beispiel: Direkt-Login ohne TOTP (falls du das brauchst)
     */
    @PostMapping("/login-direct")
    public Map<String, Object> loginDirect(@RequestBody LoginReq req) {
        UserAccount u = users.findByEmail(req.email())
                .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new IllegalArgumentException("Bad credentials"));

        String token = jwt.issueToken(u.getId(), Duration.ofHours(12));
        return Map.of("token", token);
    }
}
