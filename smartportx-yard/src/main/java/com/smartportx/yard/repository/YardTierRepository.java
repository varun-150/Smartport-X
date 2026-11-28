package com.smartportx.yard.repository;

import com.smartportx.yard.domain.YardTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface YardTierRepository extends JpaRepository<YardTier, UUID> {

    List<YardTier> findByRowIdOrderByTierNumberAsc(UUID rowId);

    List<YardTier> findByRowIdInOrderByTierNumberAsc(Collection<UUID> rowIds);

    long countByOccupiedTrue();

    long countByRowIdInAndOccupiedTrue(Collection<UUID> rowIds);

    long countByRowIdInAndOccupiedFalse(Collection<UUID> rowIds);

    Optional<YardTier> findByRowIdAndTierNumber(UUID rowId, int tierNumber);
}