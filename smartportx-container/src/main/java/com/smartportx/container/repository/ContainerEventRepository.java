package com.smartportx.container.repository;

import com.smartportx.container.domain.ContainerEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ContainerEventRepository extends JpaRepository<ContainerEvent, UUID>,
        JpaSpecificationExecutor<ContainerEvent> {
}