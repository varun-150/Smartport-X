package com.smartportx.gate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One gate visit (check-in then check-out) for an appointment. DATA-ARCHITECTURE &sect;8.3.
 */
@Entity
@Table(name = "gate_transactions", schema = "smartportx_gate")
public class GateTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gate_type", nullable = false, length = 10)
    private GateType gateType;

    @Column(name = "truck_license_plate", nullable = false, length = 20)
    private String truckLicensePlate;

    @Column(name = "driver_name", nullable = false, length = 100)
    private String driverName;

    @Column(name = "driver_license", nullable = false, length = 30)
    private String driverLicense;

    @Column(name = "container_number", nullable = false, length = 11)
    private String containerNumber;

    @Column(name = "seal_number", length = 20)
    private String sealNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "documents", columnDefinition = "jsonb")
    private Map<String, Boolean> documents;

    @Column(name = "check_in_time", nullable = false)
    private Instant checkInTime;

    @Column(name = "check_out_time")
    private Instant checkOutTime;

    @Column(name = "yard_location", length = 50)
    private String yardLocation;

    @Column(name = "gate_lane", length = 20)
    private String gateLane;

    @Column(name = "eir_number", length = 30)
    private String eirNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GateTransactionStatus status = GateTransactionStatus.CHECKED_IN;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(UUID appointmentId) {
        this.appointmentId = appointmentId;
    }

    public GateType getGateType() {
        return gateType;
    }

    public void setGateType(GateType gateType) {
        this.gateType = gateType;
    }

    public String getTruckLicensePlate() {
        return truckLicensePlate;
    }

    public void setTruckLicensePlate(String truckLicensePlate) {
        this.truckLicensePlate = truckLicensePlate;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getDriverLicense() {
        return driverLicense;
    }

    public void setDriverLicense(String driverLicense) {
        this.driverLicense = driverLicense;
    }

    public String getContainerNumber() {
        return containerNumber;
    }

    public void setContainerNumber(String containerNumber) {
        this.containerNumber = containerNumber;
    }

    public String getSealNumber() {
        return sealNumber;
    }

    public void setSealNumber(String sealNumber) {
        this.sealNumber = sealNumber;
    }

    public Map<String, Boolean> getDocuments() {
        return documents;
    }

    public void setDocuments(Map<String, Boolean> documents) {
        this.documents = documents;
    }

    public Instant getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(Instant checkInTime) {
        this.checkInTime = checkInTime;
    }

    public Instant getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(Instant checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    public String getYardLocation() {
        return yardLocation;
    }

    public void setYardLocation(String yardLocation) {
        this.yardLocation = yardLocation;
    }

    public String getGateLane() {
        return gateLane;
    }

    public void setGateLane(String gateLane) {
        this.gateLane = gateLane;
    }

    public String getEirNumber() {
        return eirNumber;
    }

    public void setEirNumber(String eirNumber) {
        this.eirNumber = eirNumber;
    }

    public GateTransactionStatus getStatus() {
        return status;
    }

    public void setStatus(GateTransactionStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}