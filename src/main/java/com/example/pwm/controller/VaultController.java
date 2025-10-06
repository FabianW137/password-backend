package com.example.pwm.controller;

import com.example.pwm.entity.UserAccount;
import com.example.pwm.entity.VaultItem;
import com.example.pwm.repo.UserAccountRepository;
import com.example.pwm.repo.VaultItemRepository;
import com.example.pwm.service.CryptoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vault")
public class VaultController {
    private final VaultItemRepository vaults;
    private final UserAccountRepository users;
    private final CryptoService crypto = new CryptoService();
    public VaultController(VaultItemRepository v, UserAccountRepository u){ this.vaults=v; this.users=u; }

    private Long currentUserId(Authentication auth){
        if (auth == null) return null;
        Object details = auth.getDetails();
        if (details instanceof Long) return (Long) details;
        return null;
    }
    @GetMapping
    public List<Map<String,Object>> list(Authentication auth){
        Long uid = currentUserId(auth);
        UserAccount owner = users.findById(uid).orElseThrow();
        return vaults.findByOwner(owner).stream().map(it -> Map.of(
            "id", it.getId(),
            "title", crypto.decrypt(it.getTitleEnc()),
            "username", crypto.decrypt(it.getUsernameEnc()),
            "password", crypto.decrypt(it.getPasswordEnc()),
            "url", crypto.decrypt(it.getUrlEnc()),
            "notes", crypto.decrypt(it.getNotesEnc())
        )).collect(Collectors.toList());
    }
    @PostMapping
    public Map<String,Object> add(Authentication auth, @RequestBody Map<String,String> body){
        Long uid = currentUserId(auth);
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
        Long uid = currentUserId(auth);
        UserAccount owner = users.findById(uid).orElseThrow();
        var it = vaults.findById(id).orElse(null);
        if (it == null || !it.getOwner().getId().equals(owner.getId())) return ResponseEntity.notFound().build();
        vaults.delete(it);
        return ResponseEntity.noContent().build();
    }
}
