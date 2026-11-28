package com.smartportx.identity.repository;

import com.smartportx.identity.domain.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, UUID> {

    List<PasswordHistory> findTop5ByUserIdOrderByCreatedAtDesc(UUID userId);
}