package com.example.pwm.controller;

import com.example.pwm.entity.UserAccount;
import com.example.pwm.entity.VaultItem;
import com.example.pwm.repo.UserAccountRepository;
import com.example.pwm.repo.VaultItemRepository;
import com.example.pwm.service.CryptoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/vault")
@CrossOrigin(origins = "*") // falls Frontend auf anderer Domain
public class VaultController {

    private final VaultItemRepository vaults;
    private final UserAccountRepository users;
    private final CryptoService crypto;

    public VaultController(VaultItemRepository vaults,
                           UserAccountRepository users,
                           CryptoService crypto) {
        this.vaults = vaults;
        this.users = users;
        this.crypto = crypto;
    }

    /* -------------------- DTOs -------------------- */
    public record VaultUpsertReq(String title, String username, String password, String url, String notes) {}
    public record VaultItemDto(Long id, String title, String username, String password, String url, String notes) {}

    /* -------------------- Auth Helper -------------------- */
    private UserAccount requireOwner(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        String name = auth.getName();
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User missing");
        }

        // 1) Versuche E-Mail
        Optional<UserAccount> byEmail = users.findByEmail(name);
        if (byEmail.isPresent()) return byEmail.get();

        // 2) Fallback: Principal ist UUID-String (z.B. JWT sub)
        try {
            UUID id = UUID.fromString(name);
            return users.findById(id).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }
    }

    private static String nz(String s) { return (s == null) ? "" : s; }

    private VaultItemDto toDto(VaultItem it) {
        return new VaultItemDto(
                it.getId(),
                crypto.decrypt(it.getTitleEnc()),
                crypto.decrypt(it.getUsernameEnc()),
                crypto.decrypt(it.getPasswordEnc()),
                crypto.decrypt(it.getUrlEnc()),
                crypto.decrypt(it.getNotesEnc())
        );
    }

    /* -------------------- Endpoints -------------------- */

    // GET /api/vault?query=abc
    @GetMapping
    public List<VaultItemDto> list(@RequestParam(name = "query", required = false) String query,
                                   Authentication auth) {
        UserAccount owner = requireOwner(auth);

        List<VaultItem> all = vaults.findByOwnerOrderByIdAsc(owner);

        // Nach Entschlüsselung filtern (da at-rest verschlüsselt)
        if (query == null || query.isBlank()) {
            return all.stream().map(this::toDto).collect(Collectors.toList());
        }

        String q = query.toLowerCase(Locale.ROOT);
        return all.stream()
                .map(this::toDto)
                .filter(dto ->
                        contains(dto.title(), q) ||
                                contains(dto.username(), q) ||
                                contains(dto.password(), q) ||
                                contains(dto.url(), q) ||
                                contains(dto.notes(), q)
                )
                .collect(Collectors.toList());
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    // POST /api/vault
    @PostMapping
    public ResponseEntity<?> add(@RequestBody VaultUpsertReq req, Authentication auth) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload fehlt");
        UserAccount owner = requireOwner(auth);

        VaultItem v = new VaultItem();
        v.setOwner(owner);
        v.setTitleEnc(crypto.encrypt(nz(req.title())));
        v.setUsernameEnc(crypto.encrypt(nz(req.username())));
        v.setPasswordEnc(crypto.encrypt(nz(req.password())));
        v.setUrlEnc(crypto.encrypt(nz(req.url())));
        v.setNotesEnc(crypto.encrypt(nz(req.notes())));
        v.setCreatedAt(Instant.now());
        v.setUpdatedAt(Instant.now());

        vaults.save(v);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", v.getId()));
    }

    // PUT /api/vault/{id}
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable("id") Long id,
                                    @RequestBody VaultUpsertReq req,
                                    Authentication auth) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload fehlt");
        UserAccount owner = requireOwner(auth);

        VaultItem v = vaults.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));

        v.setTitleEnc(crypto.encrypt(nz(req.title())));
        v.setUsernameEnc(crypto.encrypt(nz(req.username())));
        v.setPasswordEnc(crypto.encrypt(nz(req.password())));
        v.setUrlEnc(crypto.encrypt(nz(req.url())));
        v.setNotesEnc(crypto.encrypt(nz(req.notes())));
        v.setUpdatedAt(Instant.now());

        vaults.save(v);
        return ResponseEntity.ok(Map.of("id", v.getId()));
    }

    // DELETE /api/vault/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") Long id, Authentication auth) {
        UserAccount owner = requireOwner(auth);

        VaultItem v = vaults.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));

        vaults.delete(v);
        return ResponseEntity.noContent().build();
    }
}
