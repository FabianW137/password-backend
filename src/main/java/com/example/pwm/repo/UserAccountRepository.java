package com.example.pwm.repo;

import com.example.pwm.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    boolean existsByEmailIgnoreCase(String email);
    Optional<UserAccount> findByEmail(String email);
}
