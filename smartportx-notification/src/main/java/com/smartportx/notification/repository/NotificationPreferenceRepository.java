package com.smartportx.notification.repository;

import com.smartportx.notification.domain.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    java.util.Optional<NotificationPreference> findByUserId(UUID userId);
}