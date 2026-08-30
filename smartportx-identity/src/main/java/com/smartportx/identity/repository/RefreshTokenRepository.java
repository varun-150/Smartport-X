package com.smartportx.identity.repository;

import com.smartportx.identity.domain.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshTokenEntity t set t.revoked = true where t.familyId = :familyId")
    int revokeByFamily(@Param("familyId") UUID familyId);

    @Modifying
    @Query("update RefreshTokenEntity t set t.revoked = true where t.user.id = :userId")
    int revokeByUser(@Param("userId") UUID userId);
}