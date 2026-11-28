package com.smartportx.integration.repository;

import com.smartportx.integration.domain.CustomsStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomsStatusRepository extends JpaRepository<CustomsStatus, UUID> {

    Optional<CustomsStatus> findByContainerNumber(String containerNumber);
}
