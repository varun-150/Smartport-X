package com.smartportx.yard.service;

import com.smartportx.common.event.DomainEvent;
import com.smartportx.yard.domain.events.YardDomainEvents;
import com.smartportx.yard.event.EventTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class YardEventLogger {

    private final EventTransport transport;
    private final String source;

    public YardEventLogger(EventTransport transport,
                           @Value("${smartportx.yard.source}") String source) {
        this.transport = transport;
        this.source = source;
    }

    public void positionAssigned(UUID containerId, String position, String blockCode,
                                 int bayNumber, int rowNumber, int tierNumber) {
        YardDomainEvents.PositionAssignedPayload payload = new YardDomainEvents.PositionAssignedPayload(
                containerId, position, blockCode, bayNumber, rowNumber, tierNumber, java.time.Instant.now());
        transport.publish(DomainEvent.of(YardDomainEvents.POSITION_ASSIGNED, source, payload));
    }

    public void positionFreed(UUID containerId, String position) {
        YardDomainEvents.PositionFreedPayload payload = new YardDomainEvents.PositionFreedPayload(
                containerId, position, java.time.Instant.now());
        transport.publish(DomainEvent.of(YardDomainEvents.POSITION_FREED, source, payload));
    }

    public void optimizationRun(String strategy, int containerCount, Double reshufflingReduction,
                                UUID blockId, boolean dryRun) {
        YardDomainEvents.OptimizationRunPayload payload = new YardDomainEvents.OptimizationRunPayload(
                strategy, containerCount, reshufflingReduction, blockId, dryRun);
        transport.publish(DomainEvent.of(YardDomainEvents.OPTIMIZATION_RUN, source, payload));
    }

    public void equipmentStatusChanged(UUID equipmentId, String code, String previousStatus, String newStatus) {
        YardDomainEvents.EquipmentStatusChangedPayload payload = new YardDomainEvents.EquipmentStatusChangedPayload(
                equipmentId, code, previousStatus, newStatus, java.time.Instant.now());
        transport.publish(DomainEvent.of(YardDomainEvents.EQUIPMENT_STATUS_CHANGED, source, payload));
    }

    static double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}