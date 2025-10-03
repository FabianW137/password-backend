package com.example.pwm.repo;

import com.example.pwm.entity.UserAccount;
import com.example.pwm.entity.VaultItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VaultItemRepository extends JpaRepository<VaultItem, UUID> {
    List<VaultItem> findByUserOrderByCreatedAtDesc(UserAccount user);
}
