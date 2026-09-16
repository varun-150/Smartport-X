package com.smartportx.integration.repository;

import com.smartportx.integration.domain.IntegrationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IntegrationLogRepository extends JpaRepository<IntegrationLog, UUID> {

    List<IntegrationLog> findByCorrelationIdOrderByTimestampAsc(UUID correlationId);
}
