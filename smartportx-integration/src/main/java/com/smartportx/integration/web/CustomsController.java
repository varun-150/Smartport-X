package com.smartportx.integration.web;

import com.smartportx.common.error.ApiException;
import com.smartportx.integration.domain.CustomsDecision;
import com.smartportx.integration.domain.CustomsStatus;
import com.smartportx.integration.service.CustomsService;
import com.smartportx.integration.web.dto.IntegrationDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Simulated customs clearance + gate-out validation (FR-013). API-CONTRACTS &sect;8
 * customs/status + superset review/release/gate-out routes.
 */
@RestController
@RequestMapping("/api/v1/integration/customs")
public class CustomsController {

    private final CustomsService customs;

    public CustomsController(CustomsService customs) {
        this.customs = customs;
    }

    @PostMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN','TERMINAL_OP') or hasAuthority('integration:write')")
    public ResponseEntity<IntegrationDtos.CustomsStatusResponse> submit(
            @Valid @RequestBody IntegrationDtos.CustomsSubmitRequest request) {
        CustomsStatus status = customs.submit(
                request.containerNumber(), request.declarationNumber(), request.reviewedBy());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(status));
    }

    @GetMapping("/status/{containerNumber}")
    @PreAuthorize("isAuthenticated()")
    public IntegrationDtos.CustomsStatusResponse getStatus(@PathVariable String containerNumber) {
        return toResponse(customs.getOrThrow(containerNumber));
    }

    @PostMapping("/status/{containerNumber}/review")
    @PreAuthorize("hasAnyRole('ADMIN','TERMINAL_OP') or hasAuthority('integration:write')")
    public IntegrationDtos.CustomsStatusResponse review(@PathVariable String containerNumber,
                                                        @Valid @RequestBody IntegrationDtos.CustomsReviewRequest request) {
        CustomsStatus status = customs.review(containerNumber, parseDecision(request.decision()), request.reason());
        return toResponse(status);
    }

    @PostMapping("/status/{containerNumber}/release")
    @PreAuthorize("hasAnyRole('ADMIN','TERMINAL_OP') or hasAuthority('integration:write')")
    public IntegrationDtos.CustomsStatusResponse release(@PathVariable String containerNumber) {
        return toResponse(customs.release(containerNumber));
    }

    @GetMapping("/gate-out/{containerNumber}/validate")
    @PreAuthorize("isAuthenticated()")
    public IntegrationDtos.GateOutResponse validateGateOut(@PathVariable String containerNumber) {
        CustomsService.GateOutResult result = customs.validateGateOut(containerNumber);
        return new IntegrationDtos.GateOutResponse(
                result.containerNumber(), result.status(), result.allowed(), result.reason());
    }

    private CustomsDecision parseDecision(String decision) {
        if (decision == null || decision.isBlank()) {
            throw new ApiException(400, "validation_failed", "decision is required");
        }
        try {
            return CustomsDecision.valueOf(decision.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(400, "validation_failed",
                    "decision must be one of CLEARED, HELD");
        }
    }

    private IntegrationDtos.CustomsStatusResponse toResponse(CustomsStatus status) {
        return IntegrationDtos.CustomsStatusResponse.of(
                status.getContainerNumber(), status.getStatus().name(), status.getReason(),
                status.getClearedAt(), status.getReviewedBy(), status.getDeclarationNumber());
    }
}
