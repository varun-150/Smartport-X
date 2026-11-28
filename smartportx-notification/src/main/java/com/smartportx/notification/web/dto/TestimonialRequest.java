package com.smartportx.notification.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a testimonial. Bean-validation annotations are
 * enforced via {@code @Valid} in the controller; violations map to a 400
 * {@code validation_failed} problem response (see smartportx-common).
 */
public record TestimonialRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 80) String role,
        @NotBlank @Size(max = 1000) String message,
        @NotNull @Min(1) @Max(5) Integer rating) {
}