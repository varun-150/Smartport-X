package com.smartportx.container.service;

import com.smartportx.common.error.ApiException;
import com.smartportx.common.error.ConflictException;
import com.smartportx.common.error.NotFoundException;
import com.smartportx.container.domain.Container;
import com.smartportx.container.domain.ContainerMovement;
import com.smartportx.container.domain.ContainerStatusHistory;
import com.smartportx.container.repository.ContainerMovementRepository;
import com.smartportx.container.repository.ContainerRepository;
import com.smartportx.container.repository.ContainerStatusHistoryRepository;
import com.smartportx.container.util.ContainerNumbers;
import com.smartportx.container.util.ContainerStateMachine;
import com.smartportx.container.web.dto.ContainerResponse;
import com.smartportx.container.web.dto.DangerousGoodsDeclaration;
import com.smartportx.container.web.dto.MovementResponse;
import com.smartportx.container.web.dto.RegisterContainerRequest;
import com.smartportx.container.web.dto.RegisterMovementRequest;
import com.smartportx.container.web.dto.StatusChangeRequest;
import com.smartportx.container.web.dto.StatusChangeResponse;
import com.smartportx.container.web.dto.UpdateContainerRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ContainerService {

    private final ContainerRepository containers;
    private final ContainerMovementRepository movements;
    private final ContainerStatusHistoryRepository history;
    private final ContainerEventLogger eventLogger;

    public ContainerService(ContainerRepository containers, ContainerMovementRepository movements,
                            ContainerStatusHistoryRepository history, ContainerEventLogger eventLogger) {
        this.containers = containers;
        this.movements = movements;
        this.history = history;
        this.eventLogger = eventLogger;
    }

    @Transactional
    public ContainerResponse register(RegisterContainerRequest request) {
        String number = ContainerNumbers.normalize(request.containerNumber());
        if (!ContainerNumbers.isValid(number)) {
            throw new ApiException(400, "validation_failed",
                    "container number failed ISO 6346 check-digit validation");
        }
        if (containers.findByContainerNumber(number).isPresent()) {
            throw new ConflictException("Container number already registered");
        }
        Container container = Container.create(number, request.containerType());
        container.setOwner(request.owner());
        container.setConsignee(request.consignee());
        container.setShipper(request.shipper());
        container.setWeightKg(request.weight());
        container.setTareWeightKg(request.tareWeight());
        container.setMaxPayloadKg(request.maxPayload());
        container.setSealNumber(request.sealNumber());
        container.setBillOfLading(request.billOfLading());
        container.setOriginPort(request.originPort());
        container.setDestinationPort(request.destinationPort());
        container.setExpectedArrival(request.expectedArrival());
        applyDangerousGoods(container, request.dangerousGoods());
        containers.save(container);
        eventLogger.registered(container);
        return ContainerResponse.from(container);
    }

    @Transactional
    public ContainerResponse update(UUID id, UpdateContainerRequest request) {
        Container container = fetchActive(id);
        if (request.owner() != null) {
            container.setOwner(request.owner());
        }
        if (request.consignee() != null) {
            container.setConsignee(request.consignee());
        }
        if (request.shipper() != null) {
            container.setShipper(request.shipper());
        }
        if (request.weight() != null) {
            container.setWeightKg(request.weight());
        }
        if (request.tareWeight() != null) {
            container.setTareWeightKg(request.tareWeight());
        }
        if (request.maxPayload() != null) {
            container.setMaxPayloadKg(request.maxPayload());
        }
        if (request.sealNumber() != null) {
            container.setSealNumber(request.sealNumber());
        }
        if (request.billOfLading() != null) {
            container.setBillOfLading(request.billOfLading());
        }
        if (request.originPort() != null) {
            container.setOriginPort(request.originPort());
        }
        if (request.destinationPort() != null) {
            container.setDestinationPort(request.destinationPort());
        }
        if (request.expectedArrival() != null) {
            container.setExpectedArrival(request.expectedArrival());
        }
        if (request.currentLocation() != null) {
            container.setCurrentLocation(request.currentLocation());
        }
        if (request.dangerousGoods() != null) {
            applyDangerousGoods(container, request.dangerousGoods());
        }
        containers.save(container);
        return ContainerResponse.from(container);
    }

    @Transactional
    public void delete(UUID id) {
        Container container = fetchActive(id);
        container.markRemoved(Instant.now());
        containers.save(container);
        eventLogger.removed(container);
    }

    @Transactional
    public StatusChangeResponse changeStatus(UUID id, StatusChangeRequest request, UUID changedBy) {
        Container container = fetchActive(id);
        if (!ContainerStateMachine.canTransition(container.getStatus(), request.status())) {
            throw new ApiException(422, "invalid_status_transition",
                    container.getStatus().name() + " -> " + request.status().name()
                            + " is not an allowed transition");
        }
        var previous = container.getStatus();
        container.setStatus(request.status());
        if (request.location() != null && !request.location().isBlank()) {
            container.setCurrentLocation(request.location());
        }
        containers.save(container);
        history.save(ContainerStatusHistory.create(container.getId(), previous, request.status(),
                changedBy, request.reason()));
        eventLogger.statusChanged(container, previous, request.location());
        return new StatusChangeResponse(container.getId(), container.getContainerNumber(), previous,
                request.status(), container.getUpdatedAt(), request.location());
    }

    @Transactional
    public MovementResponse recordMovement(UUID id, RegisterMovementRequest request, String performedBy) {
        Container container = containers.findById(id)
                .filter(c -> c.getRemovedAt() == null)
                .orElseThrow(() -> new NotFoundException("Container " + id + " not found"));
        ContainerMovement movement = ContainerMovement.create(container.getId(),
                request.movementType(), request.from(), request.to(), request.timestamp(),
                performedBy, request.reason());
        if (request.to() != null && !request.to().isBlank()) {
            container.setCurrentLocation(request.to());
            containers.save(container);
        }
        movements.save(movement);
        eventLogger.moved(container, movement);
        return MovementResponse.from(container, movement);
    }

    private Container fetchActive(UUID id) {
        return containers.findById(id)
                .filter(c -> c.getRemovedAt() == null)
                .orElseThrow(() -> new NotFoundException("Container " + id + " not found"));
    }

    private static void applyDangerousGoods(Container container, DangerousGoodsDeclaration dg) {
        if (dg == null) {
            return;
        }
        container.setDangerousGoods(true);
        container.setUnNumber(dg.unNumber());
        container.setProperShippingName(dg.properShippingName());
        container.setImdgClass(dg.hazardClass());
        container.setPackingGroup(dg.packingGroup());
        container.setFlashPoint(dg.flashPoint() == null ? null : dg.flashPoint().toString());
        container.setEmergencyContact(dg.emergencyContact());
    }
}