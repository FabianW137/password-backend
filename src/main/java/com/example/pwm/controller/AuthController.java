package com.example.pwm.controller;

import com.example.pwm.entity.UserAccount;
import com.example.pwm.repo.UserAccountRepository;
import com.example.pwm.service.JwtService;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

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

    // ---------- DTOs ----------
    public record RegisterReq(String email, String password) {}
    public record LoginReq(String email, String password) {}

    // ---------- Endpoints ----------

    /**
     * Registrierung: prüft per findByEmail(...) statt existsByEmail(...).
     */
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> register(@RequestBody RegisterReq req) {
        if (req == null || req.email() == null || req.password() == null ||
                req.email().isBlank() || req.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email und password sind erforderlich"));
        }

        // <-- WICHTIG: existenz per findByEmail prüfen
        if (users.findByEmail(req.email()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "email bereits vergeben"));
        }

        UserAccount u = new UserAccount();
        u.setEmail(req.email().trim());
        u.setPasswordHash(encoder.encode(req.password()));
        users.save(u);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", u.getId(), "email", u.getEmail()));
    }

    /**
     * Login (direkt, ohne TOTP). Gibt ein JWT zurück.
     * Passt für JwtService-Varianten, die als Subject die User-ID tragen.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginReq req) {
        if (req == null || req.email() == null || req.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email und password sind erforderlich");
        }

        UserAccount u = users.findByEmail(req.email().trim())
                .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad credentials"));

        // Falls dein JwtService UUID erwartet:
        // String token = jwt.issueToken(u.getId(), Duration.ofHours(12));
        //
        // Falls er ein String-Subject erwartet:
        // String token = jwt.issueToken(u.getId().toString(), Duration.ofHours(12));

        // -> Standard: UUID übergeben. Passe es an deine JwtService-Signatur an.
        String token = jwt.issueToken(u.getId(), Duration.ofHours(12));

        return ResponseEntity.ok(Map.of(
                "token", token,
                "userId", u.getId(),
                "email", u.getEmail()
        ));
    }

    /**
     * Kleiner Health-/Echo-Endpoint: prüft, ob das Backend läuft.
     */
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true);
    }
}
