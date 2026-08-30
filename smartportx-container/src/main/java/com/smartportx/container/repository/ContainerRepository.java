package com.smartportx.container.repository;

import com.smartportx.container.domain.Container;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ContainerRepository extends JpaRepository<Container, UUID>, JpaSpecificationExecutor<Container> {

    Optional<Container> findByContainerNumber(String containerNumber);
}