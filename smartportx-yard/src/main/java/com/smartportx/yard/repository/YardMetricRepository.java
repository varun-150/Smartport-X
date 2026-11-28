package com.smartportx.yard.repository;

import com.smartportx.yard.domain.YardMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface YardMetricRepository extends JpaRepository<YardMetric, UUID> {

    List<YardMetric> findByBlockIdOrderByDateAsc(UUID blockId);

    List<YardMetric> findByBlockIdInOrderByDateAsc(List<UUID> blockIds);
}