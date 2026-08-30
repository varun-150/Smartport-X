package com.smartportx.container.repository;

import com.smartportx.container.domain.ContainerMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ContainerMovementRepository extends JpaRepository<ContainerMovement, UUID>,
        JpaSpecificationExecutor<ContainerMovement> {
}