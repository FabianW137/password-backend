package com.example.pwm.repo;

import com.example.pwm.entity.VaultItem;
import com.example.pwm.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VaultItemRepository extends JpaRepository<VaultItem, Long> {
    List<VaultItem> findByOwner(UserAccount owner);
}
