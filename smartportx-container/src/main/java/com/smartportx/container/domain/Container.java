package com.smartportx.container.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "containers", schema = "smartportx_container")
public class Container {

    @Id
    private UUID id;

    @Column(name = "container_number", length = 11, nullable = false, unique = true)
    private String containerNumber;

    @Column(name = "container_type", length = 10, nullable = false)
    @Convert(converter = ContainerTypeConverter.class)
    private ContainerType containerType;

    @Column(length = 100)
    private String owner;

    @Column(length = 100)
    private String consignee;

    @Column(length = 100)
    private String shipper;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "tare_weight_kg")
    private Double tareWeightKg;

    @Column(name = "max_payload_kg")
    private Double maxPayloadKg;

    @Column(name = "seal_number", length = 20)
    private String sealNumber;

    @Column(name = "bill_of_lading", length = 50)
    private String billOfLading;

    @Column(name = "origin_port", length = 100)
    private String originPort;

    @Column(name = "destination_port", length = 100)
    private String destinationPort;

    @Column(name = "expected_arrival")
    private Instant expectedArrival;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ContainerStatus status = ContainerStatus.REGISTERED;

    @Column(name = "current_location", length = 50)
    private String currentLocation;

    @Column(name = "dangerous_goods", nullable = false)
    private boolean dangerousGoods = false;

    @Column(name = "imdg_class", length = 10)
    private String imdgClass;

    @Column(name = "un_number", length = 10)
    private String unNumber;

    @Column(name = "proper_shipping_name", length = 100)
    private String properShippingName;

    @Column(name = "packing_group", length = 5)
    private String packingGroup;

    @Column(name = "flash_point", length = 20)
    private String flashPoint;

    @Column(name = "emergency_contact", length = 100)
    private String emergencyContact;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Container() {
    }

    public static Container create(String containerNumber, ContainerType containerType) {
        Container container = new Container();
        container.containerNumber = containerNumber;
        container.containerType = containerType;
        container.status = ContainerStatus.REGISTERED;
        container.dangerousGoods = false;
        return container;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.id = this.id == null ? UUID.randomUUID() : this.id;
        this.createdAt = this.createdAt == null ? now : this.createdAt;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markRemoved(Instant at) {
        this.removedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public String getContainerNumber() {
        return containerNumber;
    }

    public ContainerType getContainerType() {
        return containerType;
    }

    public String getOwner() {
        return owner;
    }

    public String getConsignee() {
        return consignee;
    }

    public String getShipper() {
        return shipper;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public Double getTareWeightKg() {
        return tareWeightKg;
    }

    public Double getMaxPayloadKg() {
        return maxPayloadKg;
    }

    public String getSealNumber() {
        return sealNumber;
    }

    public String getBillOfLading() {
        return billOfLading;
    }

    public String getOriginPort() {
        return originPort;
    }

    public String getDestinationPort() {
        return destinationPort;
    }

    public Instant getExpectedArrival() {
        return expectedArrival;
    }

    public ContainerStatus getStatus() {
        return status;
    }

    public String getCurrentLocation() {
        return currentLocation;
    }

    public boolean isDangerousGoods() {
        return dangerousGoods;
    }

    public String getImdgClass() {
        return imdgClass;
    }

    public String getUnNumber() {
        return unNumber;
    }

    public String getProperShippingName() {
        return properShippingName;
    }

    public String getPackingGroup() {
        return packingGroup;
    }

    public String getFlashPoint() {
        return flashPoint;
    }

    public String getEmergencyContact() {
        return emergencyContact;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void setConsignee(String consignee) {
        this.consignee = consignee;
    }

    public void setShipper(String shipper) {
        this.shipper = shipper;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public void setTareWeightKg(Double tareWeightKg) {
        this.tareWeightKg = tareWeightKg;
    }

    public void setMaxPayloadKg(Double maxPayloadKg) {
        this.maxPayloadKg = maxPayloadKg;
    }

    public void setSealNumber(String sealNumber) {
        this.sealNumber = sealNumber;
    }

    public void setBillOfLading(String billOfLading) {
        this.billOfLading = billOfLading;
    }

    public void setOriginPort(String originPort) {
        this.originPort = originPort;
    }

    public void setDestinationPort(String destinationPort) {
        this.destinationPort = destinationPort;
    }

    public void setExpectedArrival(Instant expectedArrival) {
        this.expectedArrival = expectedArrival;
    }

    public void setStatus(ContainerStatus status) {
        this.status = status;
    }

    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }

    public void setDangerousGoods(boolean dangerousGoods) {
        this.dangerousGoods = dangerousGoods;
    }

    public void setImdgClass(String imdgClass) {
        this.imdgClass = imdgClass;
    }

    public void setUnNumber(String unNumber) {
        this.unNumber = unNumber;
    }

    public void setProperShippingName(String properShippingName) {
        this.properShippingName = properShippingName;
    }

    public void setPackingGroup(String packingGroup) {
        this.packingGroup = packingGroup;
    }

    public void setFlashPoint(String flashPoint) {
        this.flashPoint = flashPoint;
    }

    public void setEmergencyContact(String emergencyContact) {
        this.emergencyContact = emergencyContact;
    }
}