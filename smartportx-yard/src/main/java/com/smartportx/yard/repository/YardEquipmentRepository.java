package com.smartportx.yard.repository;

import com.smartportx.yard.domain.EquipmentStatus;
import com.smartportx.yard.domain.EquipmentType;
import com.smartportx.yard.domain.YardEquipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface YardEquipmentRepository extends JpaRepository<YardEquipment, UUID> {

    List<YardEquipment> findAllByOrderByCodeAsc();

    List<YardEquipment> findByEquipmentTypeOrderByCodeAsc(EquipmentType type);

    List<YardEquipment> findByStatusOrderByCodeAsc(EquipmentStatus status);

    List<YardEquipment> findByEquipmentTypeAndStatusOrderByCodeAsc(EquipmentType type, EquipmentStatus status);

    List<YardEquipment> findByAssignedBlockId(UUID blockId);

    List<YardEquipment> findByAssignedBlockIdAndStatusIn(UUID blockId, Collection<EquipmentStatus> statuses);

    Optional<YardEquipment> findByCode(String code);
}