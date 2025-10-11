package com.example.pwm.repo;

import com.example.pwm.entity.UserAccount;
import com.example.pwm.entity.VaultItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VaultItemRepository extends JpaRepository<VaultItem, Long> {

    // für Listenansicht (stabil sortiert)
    List<VaultItem> findByOwnerOrderByIdAsc(UserAccount owner);

    // Ownership-sichere Zugriffe
    Optional<VaultItem> findByIdAndOwner(Long id, UserAccount owner);
}
