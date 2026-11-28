package com.smartportx.container.web.dto;

import com.smartportx.container.domain.Container;
import com.smartportx.container.domain.ContainerStatus;

import java.time.Instant;
import java.util.UUID;

public record ContainerResponse(
        UUID id,
        String containerNumber,
        String containerType,
        ContainerStatus status,
        String owner,
        String consignee,
        String shipper,
        Double weight,
        Double tareWeight,
        Double maxPayload,
        String sealNumber,
        String billOfLading,
        String originPort,
        String destinationPort,
        Instant expectedArrival,
        String currentLocation,
        boolean dangerousGoods,
        String unNumber,
        String properShippingName,
        String hazardClass,
        String packingGroup,
        String flashPoint,
        String emergencyContact,
        Instant createdAt,
        Instant updatedAt) {

    public static ContainerResponse from(Container c) {
        return new ContainerResponse(
                c.getId(), c.getContainerNumber(), c.getContainerType().value(), c.getStatus(),
                c.getOwner(), c.getConsignee(), c.getShipper(), c.getWeightKg(), c.getTareWeightKg(),
                c.getMaxPayloadKg(), c.getSealNumber(), c.getBillOfLading(), c.getOriginPort(),
                c.getDestinationPort(), c.getExpectedArrival(), c.getCurrentLocation(),
                c.isDangerousGoods(), c.getUnNumber(), c.getProperShippingName(), c.getImdgClass(),
                c.getPackingGroup(), c.getFlashPoint(), c.getEmergencyContact(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}