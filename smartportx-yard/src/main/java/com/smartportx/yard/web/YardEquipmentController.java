package com.smartportx.yard.web;

import com.smartportx.yard.domain.EquipmentStatus;
import com.smartportx.yard.domain.EquipmentType;
import com.smartportx.yard.domain.YardEquipment;
import com.smartportx.yard.service.YardEquipmentService;
import com.smartportx.yard.web.dto.EquipmentResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/yard/equipment")
public class YardEquipmentController {

    private final YardEquipmentService service;

    public YardEquipmentController(YardEquipmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public EquipmentResponse.ListResponse list(@RequestParam(required = false) EquipmentType type,
                                               @RequestParam(required = false) EquipmentStatus status) {
        List<EquipmentResponse> data = service.list(type, status).stream()
                .map(EquipmentResponse::from)
                .toList();
        return new EquipmentResponse.ListResponse(data);
    }

    @GetMapping("/{equipmentId}")
    @PreAuthorize("isAuthenticated()")
    public EquipmentResponse get(@PathVariable UUID equipmentId) {
        return EquipmentResponse.from(service.get(equipmentId));
    }

    @PutMapping("/{equipmentId}")
    @PreAuthorize("hasAnyRole('ADMIN','YARD_MANAGER','TERMINAL_OP')")
    public EquipmentResponse update(@PathVariable UUID equipmentId,
                                    @RequestBody EquipmentResponse.UpdateRequest body) {
        YardEquipment equipment = service.update(equipmentId, body.status(), body.assignedBlockId());
        return EquipmentResponse.from(equipment);
    }

    @GetMapping("/{equipmentId}/moves")
    @PreAuthorize("isAuthenticated()")
    public EquipmentResponse.MovesResponse moves(@PathVariable UUID equipmentId) {
        return EquipmentResponse.MovesResponse.from(service.get(equipmentId));
    }
}