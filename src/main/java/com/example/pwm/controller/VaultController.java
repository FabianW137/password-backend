package com.example.pwm.controller;

import com.example.pwm.entity.UserAccount;
import com.example.pwm.entity.VaultItem;
import com.example.pwm.repo.UserAccountRepository;
import com.example.pwm.repo.VaultItemRepository;
import com.example.pwm.service.CryptoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vault")
public class VaultController {
    private final VaultItemRepository vaults;
    private final UserAccountRepository users;
    private final CryptoService crypto = new CryptoService();
    public VaultController(VaultItemRepository v, UserAccountRepository u){ this.vaults=v; this.users=u; }

    private UUID currentUserId(Authentication auth) {
        if (auth == null) throw new IllegalStateException("no Authentication");

        // 1) Bevorzugt: Das Subject aus dem JWT steckt als Name (String) drin
        String name = auth.getName();
        if (name != null) {
            try { return UUID.fromString(name); } catch (IllegalArgumentException ignored) {}
        }

        // 2) Oder wurde im Filter in die Details gelegt
        Object details = auth.getDetails();
        if (details instanceof UUID u) return u;
        if (details instanceof String s) {
            try { return UUID.fromString(s); } catch (IllegalArgumentException ignored) {}
        }

        // 3) Fallback: Principal trägt die E-Mail -> per Repo auflösen
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails ud) {
            return users.findByEmail(ud.getUsername())
                    .map(UserAccount::getId)     // UserAccount.id ist UUID
                    .orElseThrow(() -> new IllegalStateException("unknown user: " + ud.getUsername()));
        }

        throw new IllegalStateException("cannot determine current user id");
    }
    @GetMapping
    public List<Map<String, Serializable>> list(Authentication auth) {
        UUID uid = currentUserId(auth);
        UserAccount owner = users.findById(uid).orElseThrow();

        return vaults.findByOwner(owner).stream()
                .map(it -> {
                    Map<String, Serializable> m = new HashMap<>();
                    m.put("id", it.getId());                              // Long ist Serializable
                    m.put("title",     crypto.decrypt(it.getTitleEnc()));
                    m.put("username",  crypto.decrypt(it.getUsernameEnc()));
                    m.put("password",  crypto.decrypt(it.getPasswordEnc()));
                    m.put("url",       crypto.decrypt(it.getUrlEnc()));
                    m.put("notes",     crypto.decrypt(it.getNotesEnc()));
                    return m;
                })
                .collect(Collectors.toList());
    }
    @PostMapping
    public Map<String,Object> add(Authentication auth, @RequestBody Map<String,String> body){
        UUID uid = currentUserId(auth);
        UserAccount owner = users.findById(uid).orElseThrow();
        VaultItem it = new VaultItem();
        it.setOwner(owner);
        it.setTitleEnc(crypto.encrypt(body.get("title")));
        it.setUsernameEnc(crypto.encrypt(body.get("username")));
        it.setPasswordEnc(crypto.encrypt(body.get("password")));
        it.setUrlEnc(crypto.encrypt(body.get("url")));
        it.setNotesEnc(crypto.encrypt(body.get("notes")));
        vaults.save(it);
        return Map.of("id", it.getId());
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(Authentication auth, @PathVariable Long id){
        UUID uid = currentUserId(auth);
        UserAccount owner = users.findById(uid).orElseThrow();
        var it = vaults.findById(id).orElse(null);
        if (it == null || !it.getOwner().getId().equals(owner.getId())) return ResponseEntity.notFound().build();
        vaults.delete(it);
        return ResponseEntity.noContent().build();
    }
}
