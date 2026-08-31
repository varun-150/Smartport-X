package com.smartportx.yard.repository;

import com.smartportx.yard.domain.YardRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface YardRowRepository extends JpaRepository<YardRow, UUID> {

    List<YardRow> findByBayIdOrderByRowNumberAsc(UUID bayId);

    List<YardRow> findByBayIdInOrderByRowNumberAsc(Collection<UUID> bayIds);
}