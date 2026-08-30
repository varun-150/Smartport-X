package com.smartportx.container.repository;

import com.smartportx.container.domain.ContainerStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface ContainerStatusHistoryRepository extends JpaRepository<ContainerStatusHistory, UUID>,
        JpaSpecificationExecutor<ContainerStatusHistory> {

    List<ContainerStatusHistory> findByContainerIdOrderByChangedAtDesc(UUID containerId);
}