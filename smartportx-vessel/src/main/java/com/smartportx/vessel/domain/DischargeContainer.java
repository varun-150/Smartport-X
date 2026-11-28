package com.smartportx.vessel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "discharge_containers", schema = "smartportx_vessel")
public class DischargeContainer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "discharge_plan_id", nullable = false)
    private UUID dischargePlanId;

    @Column(name = "container_number", nullable = false, length = 11)
    private String containerNumber;

    @Column(name = "container_type", nullable = false, length = 10)
    private String containerType;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "dangerous_goods", nullable = false)
    private boolean dangerousGoods;

    @Column(name = "reefer", nullable = false)
    private boolean reefer;

    @Column(name = "vessel_position", nullable = false, length = 10)
    private String vesselPosition;

    @Column(name = "yard_target", length = 20)
    private String yardTarget;

    @Column(name = "priority", nullable = false, length = 10)
    private String priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DischargeContainerStatus status = DischargeContainerStatus.PLANNED;

    @Column(name = "actual_yard_position", length = 20)
    private String actualYardPosition;

    @Column(name = "equipment_id", length = 20)
    private String equipmentId;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "notes")
    private String notes;

    @Column(name = "discharged_at")
    private Instant dischargedAt;

    @Column(name = "stacked_at")
    private Instant stackedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDischargePlanId() { return dischargePlanId; }
    public void setDischargePlanId(UUID dischargePlanId) { this.dischargePlanId = dischargePlanId; }

    public String getContainerNumber() { return containerNumber; }
    public void setContainerNumber(String containerNumber) { this.containerNumber = containerNumber; }

    public String getContainerType() { return containerType; }
    public void setContainerType(String containerType) { this.containerType = containerType; }

    public Double getWeightKg() { return weightKg; }
    public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }

    public boolean isDangerousGoods() { return dangerousGoods; }
    public void setDangerousGoods(boolean dangerousGoods) { this.dangerousGoods = dangerousGoods; }

    public boolean isReefer() { return reefer; }
    public void setReefer(boolean reefer) { this.reefer = reefer; }

    public String getVesselPosition() { return vesselPosition; }
    public void setVesselPosition(String vesselPosition) { this.vesselPosition = vesselPosition; }

    public String getYardTarget() { return yardTarget; }
    public void setYardTarget(String yardTarget) { this.yardTarget = yardTarget; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public DischargeContainerStatus getStatus() { return status; }
    public void setStatus(DischargeContainerStatus status) { this.status = status; }

    public String getActualYardPosition() { return actualYardPosition; }
    public void setActualYardPosition(String actualYardPosition) { this.actualYardPosition = actualYardPosition; }

    public String getEquipmentId() { return equipmentId; }
    public void setEquipmentId(String equipmentId) { this.equipmentId = equipmentId; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getDischargedAt() { return dischargedAt; }
    public void setDischargedAt(Instant dischargedAt) { this.dischargedAt = dischargedAt; }

    public Instant getStackedAt() { return stackedAt; }
    public void setStackedAt(Instant stackedAt) { this.stackedAt = stackedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}