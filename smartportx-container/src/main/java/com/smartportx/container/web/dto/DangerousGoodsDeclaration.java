package com.smartportx.container.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DangerousGoodsDeclaration(
        @NotBlank @Size(max = 10) String unNumber,
        @NotBlank @Size(max = 100) String properShippingName,
        @NotBlank @Size(max = 10) String hazardClass,
        @Pattern(regexp = "I|II|III") String packingGroup,
        Double flashPoint,
        @Size(max = 100) String emergencyContact) {
}