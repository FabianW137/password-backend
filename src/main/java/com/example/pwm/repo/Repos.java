package com.example.pwm.repo;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.pwm.entity.*;
import java.util.*;
public interface UserAccountRepository extends JpaRepository<UserAccount, java.util.UUID> { Optional<UserAccount> findByEmail(String email); }
public interface VaultItemRepository extends JpaRepository<VaultItem, java.util.UUID> { List<VaultItem> findByUserOrderByCreatedAtDesc(UserAccount user); }
