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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vessel_schedules", schema = "smartportx_vessel",
       uniqueConstraints = @UniqueConstraint(columnNames = {"vessel_id", "voyage_number"}))
public class VesselSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "vessel_id", nullable = false)
    private UUID vesselId;

    @NotNull
    @Column(name = "voyage_number", nullable = false, length = 20)
    private String voyageNumber;

    @NotNull
    @Column(name = "berth_number", nullable = false, length = 20)
    private String berthNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private SchedulePriority priority = SchedulePriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VesselScheduleStatus status = VesselScheduleStatus.SCHEDULED;

    @NotNull
    @Column(name = "eta", nullable = false)
    private Instant eta;

    @NotNull
    @Column(name = "etd", nullable = false)
    private Instant etd;

    @Column(name = "port_of_call", length = 100)
    private String portOfCall;

    @Column(name = "next_port", length = 100)
    private String nextPort;

    @Column(name = "estimated_teus")
    private Integer estimatedTeus;

    @Column(name = "actual_teus")
    private Integer actualTeUs;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "departed_at")
    private Instant departedAt;

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

    public UUID getVesselId() { return vesselId; }
    public void setVesselId(UUID vesselId) { this.vesselId = vesselId; }

    public String getVoyageNumber() { return voyageNumber; }
    public void setVoyageNumber(String voyageNumber) { this.voyageNumber = voyageNumber; }

    public String getBerthNumber() { return berthNumber; }
    public void setBerthNumber(String berthNumber) { this.berthNumber = berthNumber; }

    public SchedulePriority getPriority() { return priority; }
    public void setPriority(SchedulePriority priority) { this.priority = priority; }

    public VesselScheduleStatus getStatus() { return status; }
    public void setStatus(VesselScheduleStatus status) { this.status = status; }

    public Instant getEta() { return eta; }
    public void setEta(Instant eta) { this.eta = eta; }

    public Instant getEtd() { return etd; }
    public void setEtd(Instant etd) { this.etd = etd; }

    public String getPortOfCall() { return portOfCall; }
    public void setPortOfCall(String portOfCall) { this.portOfCall = portOfCall; }

    public String getNextPort() { return nextPort; }
    public void setNextPort(String nextPort) { this.nextPort = nextPort; }

    public Integer getEstimatedTeUs() { return estimatedTeus; }
    public void setEstimatedTeUs(Integer estimatedTeUs) { this.estimatedTeus = estimatedTeUs; }

    public Integer getActualTeUs() { return actualTeUs; }
    public void setActualTeUs(Integer actualTeUs) { this.actualTeUs = actualTeUs; }

    public Instant getArrivedAt() { return arrivedAt; }
    public void setArrivedAt(Instant arrivedAt) { this.arrivedAt = arrivedAt; }

    public Instant getDepartedAt() { return departedAt; }
    public void setDepartedAt(Instant departedAt) { this.departedAt = departedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}