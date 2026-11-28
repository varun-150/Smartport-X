package com.smartportx.notification.repository;

import com.smartportx.notification.domain.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, UUID> {

    List<NotificationHistory> findByUserIdOrderBySentAtDesc(UUID userId);

    long countByCategory(com.smartportx.notification.domain.NotificationCategory category);
}