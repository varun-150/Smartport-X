package com.smartportx.yard.repository;

import com.smartportx.yard.domain.OptimizationHistory;
import com.smartportx.yard.domain.OptimizationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface OptimizationHistoryRepository
        extends JpaRepository<OptimizationHistory, UUID>, JpaSpecificationExecutor<OptimizationHistory> {

    long countByBlockId(UUID blockId);

    long countByBlockIdAndStatus(UUID blockId, OptimizationStatus status);

    List<OptimizationHistory> findByBlockIdOrderByCreatedAtDesc(UUID blockId, Pageable pageable);

    List<OptimizationHistory> findByStatusOrderByCreatedAtDesc(OptimizationStatus status, Pageable pageable);

    List<OptimizationHistory> findByBlockIdAndStatusOrderByCreatedAtDesc(UUID blockId, OptimizationStatus status,
                                                                        Pageable pageable);
}