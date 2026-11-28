package com.smartportx.gate.repository;

import com.smartportx.gate.domain.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QueueStatusRepository extends JpaRepository<QueueStatus, UUID> {

    Optional<QueueStatus> findFirstByOrderBySnapshotTimeDesc();
}