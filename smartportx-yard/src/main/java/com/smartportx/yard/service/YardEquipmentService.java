package com.smartportx.yard.service;

import com.smartportx.common.error.ApiException;
import com.smartportx.common.error.NotFoundException;
import com.smartportx.yard.domain.EquipmentStatus;
import com.smartportx.yard.domain.EquipmentType;
import com.smartportx.yard.domain.YardBlock;
import com.smartportx.yard.domain.YardEquipment;
import com.smartportx.yard.repository.YardBlockRepository;
import com.smartportx.yard.repository.YardEquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class YardEquipmentService {

    private final YardEquipmentRepository equipmentRepository;
    private final YardBlockRepository blockRepository;
    private final YardEventLogger events;

    public YardEquipmentService(YardEquipmentRepository equipmentRepository, YardBlockRepository blockRepository,
                                YardEventLogger events) {
        this.equipmentRepository = equipmentRepository;
        this.blockRepository = blockRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<YardEquipment> list(EquipmentType type, EquipmentStatus status) {
        if (type != null && status != null) {
            return equipmentRepository.findByEquipmentTypeAndStatusOrderByCodeAsc(type, status);
        }
        if (type != null) {
            return equipmentRepository.findByEquipmentTypeOrderByCodeAsc(type);
        }
        if (status != null) {
            return equipmentRepository.findByStatusOrderByCodeAsc(status);
        }
        return equipmentRepository.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public YardEquipment get(UUID equipmentId) {
        return equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new NotFoundException("Equipment " + equipmentId + " not found"));
    }

    @Transactional
    public YardEquipment update(UUID equipmentId, EquipmentStatus status, UUID assignedBlockId) {
        YardEquipment equipment = get(equipmentId);
        if (equipment.getStatus() == EquipmentStatus.OUT_OF_SERVICE && status == null && assignedBlockId == null) {
            throw new ApiException(409, "equipment_out_of_service",
                    "Equipment " + equipment.getCode() + " is out of service");
        }
        YardBlock targetBlock = null;
        if (assignedBlockId != null) {
            targetBlock = blockRepository.findById(assignedBlockId)
                    .orElseThrow(() -> new NotFoundException("Block " + assignedBlockId + " not found"));
        }
        EquipmentStatus previous = equipment.getStatus();
        if (status != null) {
            equipment.setStatus(status);
        }
        if (assignedBlockId != null) {
            equipment.setAssignedBlockId(assignedBlockId);
        }
        if (equipment.getStatus() == EquipmentStatus.OUT_OF_SERVICE) {
            equipment.setAssignedBlockId(null);
        }
        equipmentRepository.save(equipment);
        EquipmentStatus current = equipment.getStatus();
        if (previous != current) {
            events.equipmentStatusChanged(equipment.getId(), equipment.getCode(), previous.name(), current.name());
        }
        return equipment;
    }
}