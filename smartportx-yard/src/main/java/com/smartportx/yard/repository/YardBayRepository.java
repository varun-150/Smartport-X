package com.smartportx.yard.repository;

import com.smartportx.yard.domain.YardBay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface YardBayRepository extends JpaRepository<YardBay, UUID> {

    List<YardBay> findByBlockIdOrderByBayNumberAsc(UUID blockId);

    List<YardBay> findByBlockIdInOrderByBayNumberAsc(Collection<UUID> blockIds);
}