package com.smartportx.gate.domain;

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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Pre-announced truck appointment. DATA-ARCHITECTURE &sect;8.2.
 */
@Entity
@Table(name = "appointments", schema = "smartportx_gate")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "confirmation_number", nullable = false, length = 20, unique = true)
    private String confirmationNumber;

    @Column(name = "trucking_company", nullable = false, length = 100)
    private String truckingCompany;

    @Column(name = "truck_license_plate", nullable = false, length = 20)
    private String truckLicensePlate;

    @Column(name = "driver_name", nullable = false, length = 100)
    private String driverName;

    @Column(name = "driver_license", nullable = false, length = 30)
    private String driverLicense;

    @Enumerated(EnumType.STRING)
    @Column(name = "appointment_type", nullable = false, length = 10)
    private AppointmentType appointmentType;

    @Column(name = "container_number", nullable = false, length = 11)
    private String containerNumber;

    @Column(name = "preferred_time_slot", nullable = false)
    private Instant preferredTimeSlot;

    @Column(name = "actual_arrival")
    private Instant actualArrival;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 30;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(name = "yard_location", length = 50)
    private String yardLocation;

    @Column(name = "gate_lane", length = 20)
    private String gateLane;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "special_requirements", columnDefinition = "jsonb")
    private Map<String, Boolean> specialRequirements;

    @Column(name = "notes")
    private String notes;

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

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getConfirmationNumber() {
        return confirmationNumber;
    }

    public void setConfirmationNumber(String confirmationNumber) {
        this.confirmationNumber = confirmationNumber;
    }

    public String getTruckingCompany() {
        return truckingCompany;
    }

    public void setTruckingCompany(String truckingCompany) {
        this.truckingCompany = truckingCompany;
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

    public AppointmentType getAppointmentType() {
        return appointmentType;
    }

    public void setAppointmentType(AppointmentType appointmentType) {
        this.appointmentType = appointmentType;
    }

    public String getContainerNumber() {
        return containerNumber;
    }

    public void setContainerNumber(String containerNumber) {
        this.containerNumber = containerNumber;
    }

    public Instant getPreferredTimeSlot() {
        return preferredTimeSlot;
    }

    public void setPreferredTimeSlot(Instant preferredTimeSlot) {
        this.preferredTimeSlot = preferredTimeSlot;
    }

    public Instant getActualArrival() {
        return actualArrival;
    }

    public void setActualArrival(Instant actualArrival) {
        this.actualArrival = actualArrival;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
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

    public Map<String, Boolean> getSpecialRequirements() {
        return specialRequirements;
    }

    public void setSpecialRequirements(Map<String, Boolean> specialRequirements) {
        this.specialRequirements = specialRequirements;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant windowStart() {
        return preferredTimeSlot.minusSeconds(15L * 60);
    }

    public Instant windowEnd() {
        return preferredTimeSlot.plusSeconds(15L * 60);
    }
}