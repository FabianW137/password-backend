package com.example.pwm.controller;

import com.example.pwm.entity.UserAccount;
import com.example.pwm.entity.VaultItem;
import com.example.pwm.repo.UserAccountRepository;
import com.example.pwm.repo.VaultItemRepository;
import com.example.pwm.service.CryptoService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

@Controller
public class GraphQLVaultController {
    private final VaultItemRepository vaults;
    private final UserAccountRepository users;
    private final CryptoService crypto;

    public GraphQLVaultController(VaultItemRepository vaults, UserAccountRepository users, CryptoService crypto) {
        this.vaults = vaults;
        this.users = users;
        this.crypto = crypto;
    }

    /* DTOs */
    public record UserDto(String id, String email, String createdAt) {}
    public record VaultItemDto(Long id, String title, String username, String password, String url, String notes, String createdAt, String updatedAt) {}
    public record VaultUpsertInput(String title, String username, String password, String url, String notes) {}

    private UserAccount requireOwner(Authentication auth) {
        if (auth == null || auth.getName() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not authenticated");
        return users.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "owner not found"));
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private VaultItemDto toDto(VaultItem v) {
        return new VaultItemDto(
                v.getId(),
                crypto.decrypt(nz(v.getTitleEnc())),
                crypto.decrypt(nz(v.getUsernameEnc())),
                crypto.decrypt(nz(v.getPasswordEnc())),
                crypto.decrypt(nz(v.getUrlEnc())),
                crypto.decrypt(nz(v.getNotesEnc())),
                v.getCreatedAt() != null ? v.getCreatedAt().toString() : null,
                v.getUpdatedAt() != null ? v.getUpdatedAt().toString() : null
        );
    }

    /* Queries */
    @QueryMapping
    public UserDto me(Authentication auth) {
        UserAccount u = requireOwner(auth);
        return new UserDto(
                u.getId() != null ? u.getId().toString() : null,
                u.getEmail(),
                u.getCreatedAt() != null ? u.getCreatedAt().toString() : null
        );
    }

    @QueryMapping
    public List<VaultItemDto> vaultItems(Authentication auth) {
        UserAccount owner = requireOwner(auth);
        return vaults.findByOwnerOrderByIdAsc(owner).stream().map(this::toDto).toList();
    }

    @QueryMapping
    public VaultItemDto vaultItem(@Argument Long id, Authentication auth) {
        UserAccount owner = requireOwner(auth);
        VaultItem v = vaults.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        return toDto(v);
    }

    /* Mutations */
    @MutationMapping
    public VaultItemDto createVaultItem(@Argument VaultUpsertInput input, Authentication auth) {
        if (input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload fehlt");
        UserAccount owner = requireOwner(auth);

        VaultItem v = new VaultItem();
        v.setOwner(owner);
        v.setTitleEnc(crypto.encrypt(nz(input.title())));
        v.setUsernameEnc(crypto.encrypt(nz(input.username())));
        v.setPasswordEnc(crypto.encrypt(nz(input.password())));
        v.setUrlEnc(crypto.encrypt(nz(input.url())));
        v.setNotesEnc(crypto.encrypt(nz(input.notes())));
        v.setCreatedAt(Instant.now());
        v.setUpdatedAt(null);

        VaultItem saved = vaults.save(v);
        return toDto(saved);
    }

    @MutationMapping
    public VaultItemDto updateVaultItem(@Argument Long id, @Argument VaultUpsertInput input, Authentication auth) {
        if (input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload fehlt");
        UserAccount owner = requireOwner(auth);

        VaultItem v = vaults.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));

        if (input.title() != null)    v.setTitleEnc(crypto.encrypt(nz(input.title())));
        if (input.username() != null) v.setUsernameEnc(crypto.encrypt(nz(input.username())));
        if (input.password() != null) v.setPasswordEnc(crypto.encrypt(nz(input.password())));
        if (input.url() != null)      v.setUrlEnc(crypto.encrypt(nz(input.url())));
        if (input.notes() != null)    v.setNotesEnc(crypto.encrypt(nz(input.notes())));
        v.setUpdatedAt(Instant.now());

        VaultItem saved = vaults.save(v);
        return toDto(saved);
    }

    @MutationMapping
    public Boolean deleteVaultItem(@Argument Long id, Authentication auth) {
        UserAccount owner = requireOwner(auth);
        VaultItem v = vaults.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        vaults.delete(v);
        return Boolean.TRUE;
    }
}
