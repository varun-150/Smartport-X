package com.smartportx.container.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateContainerRequest(
        @Size(max = 100) String owner,
        @Size(max = 100) String consignee,
        @Size(max = 100) String shipper,
        @Positive Double weight,
        @Positive Double tareWeight,
        @Positive Double maxPayload,
        @Size(max = 20) String sealNumber,
        @Size(max = 50) String billOfLading,
        @Pattern(regexp = "[A-Z]{5}") String originPort,
        @Pattern(regexp = "[A-Z]{5}") String destinationPort,
        Instant expectedArrival,
        @Size(max = 50) String currentLocation,
        @Valid DangerousGoodsDeclaration dangerousGoods) {
}