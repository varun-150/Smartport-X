package com.smartportx.identity.repository;

import com.smartportx.identity.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRole.UserRoleId> {

    void deleteByUserId(UUID userId);
}