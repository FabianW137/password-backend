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

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vault")
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

    /* -------------------- Helpers -------------------- */
    private UUID currentUserId(Authentication auth) {
        if (auth == null) return null;
        Object d = auth.getDetails();
        return (d instanceof UUID) ? (UUID) d : null;
    }

    private UserAccount requireOwner(Authentication auth) {
        UUID uid = currentUserId(auth);
        if (uid == null) throw new IllegalStateException("No user in context");
        return users.findById(uid).orElseThrow();
    }

    /* -------------------- Endpoints -------------------- */

    @GetMapping
    public List<Map<String, Object>> list(Authentication auth) {
        UserAccount owner = requireOwner(auth);

        return vaults.findByOwner(owner).stream()
                .sorted(Comparator.comparing(VaultItem::getId)) // optional
                .map(it -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",       it.getId());
                    m.put("title",    crypto.decrypt(it.getTitleEnc()));
                    m.put("username", crypto.decrypt(it.getUsernameEnc()));
                    m.put("password", crypto.decrypt(it.getPasswordEnc()));
                    m.put("url",      crypto.decrypt(it.getUrlEnc()));
                    m.put("notes",    crypto.decrypt(it.getNotesEnc()));
                    return m;
                })
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody VaultUpsertReq req, Authentication auth) {
        if (req == null) return ResponseEntity.badRequest().body(Map.of("error", "payload fehlt"));
        UserAccount owner = requireOwner(auth);

        VaultItem v = new VaultItem();
        v.setOwner(owner);
        v.setTitleEnc(crypto.encrypt(nullToEmpty(req.title())));
        v.setUsernameEnc(crypto.encrypt(nullToEmpty(req.username())));
        v.setPasswordEnc(crypto.encrypt(nullToEmpty(req.password())));
        v.setUrlEnc(crypto.encrypt(nullToEmpty(req.url())));
        v.setNotesEnc(crypto.encrypt(nullToEmpty(req.notes())));
        v.setCreatedAt(Instant.now());

        vaults.save(v);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", v.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") Long id, Authentication auth) {
        UserAccount owner = requireOwner(auth);
        Optional<VaultItem> opt = vaults.findById(id);
        if (opt.isEmpty() || !owner.equals(opt.get().getOwner())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not found"));
        }
        vaults.delete(opt.get());
        return ResponseEntity.noContent().build();
    }

    private static String nullToEmpty(String s) {
        return (s == null) ? "" : s;
    }
}
