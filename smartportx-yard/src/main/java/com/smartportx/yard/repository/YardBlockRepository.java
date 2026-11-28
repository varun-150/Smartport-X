package com.smartportx.yard.repository;

import com.smartportx.yard.domain.YardBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface YardBlockRepository extends JpaRepository<YardBlock, UUID> {

    List<YardBlock> findAllByOrderByBlockCodeAsc();

    Optional<YardBlock> findByBlockCode(String blockCode);

    List<YardBlock> findByBlockCodeIn(Collection<String> blockCodes);
}