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
@Table(name = "vessels", schema = "smartportx_vessel")
public class Vessel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "imo_number", nullable = false, length = 10, unique = true)
    private String imoNumber;

    @Column(name = "vessel_name", nullable = false, length = 100)
    private String vesselName;

    @Enumerated(EnumType.STRING)
    @Column(name = "vessel_type", nullable = false, length = 20)
    private VesselType vesselType;

    @Column(name = "length_m", nullable = false)
    private double lengthM;

    @Column(name = "beam_m", nullable = false)
    private double beamM;

    @Column(name = "draft_m", nullable = false)
    private double draftM;

    @Column(name = "teu_capacity")
    private Integer teuCapacity;

    @Column(name = "gross_tonnage")
    private Double grossTonnage;

    @Column(name = "call_sign", length = 10)
    private String callSign;

    @Column(name = "flag", length = 50)
    private String flag;

    @Column(name = "operator", length = 100)
    private String operator;

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

    public String getImoNumber() { return imoNumber; }
    public void setImoNumber(String imoNumber) { this.imoNumber = imoNumber; }

    public String getVesselName() { return vesselName; }
    public void setVesselName(String vesselName) { this.vesselName = vesselName; }

    public VesselType getVesselType() { return vesselType; }
    public void setVesselType(VesselType vesselType) { this.vesselType = vesselType; }

    public double getLengthM() { return lengthM; }
    public void setLengthM(double lengthM) { this.lengthM = lengthM; }

    public double getBeamM() { return beamM; }
    public void setBeamM(double beamM) { this.beamM = beamM; }

    public double getDraftM() { return draftM; }
    public void setDraftM(double draftM) { this.draftM = draftM; }

    public Integer getTeuCapacity() { return teuCapacity; }
    public void setTeuCapacity(Integer teuCapacity) { this.teuCapacity = teuCapacity; }

    public Double getGrossTonnage() { return grossTonnage; }
    public void setGrossTonnage(Double grossTonnage) { this.grossTonnage = grossTonnage; }

    public String getCallSign() { return callSign; }
    public void setCallSign(String callSign) { this.callSign = callSign; }

    public String getFlag() { return flag; }
    public void setFlag(String flag) { this.flag = flag; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}