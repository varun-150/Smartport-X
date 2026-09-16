package com.smartportx.gate.web;

import com.smartportx.common.error.ApiException;
import com.smartportx.gate.domain.AppointmentQuota;
import com.smartportx.gate.service.QuotaService;
import com.smartportx.gate.support.GateIds;
import com.smartportx.gate.web.dto.QuotaDtos;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quotas")
public class QuotaController {

    private final QuotaService quotas;

    public QuotaController(QuotaService quotas) {
        this.quotas = quotas;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public QuotaDtos.QuotaListResponse day(@RequestParam(defaultValue = "2026-06-08") String date) {
        LocalDate day = LocalDate.parse(date);
        List<QuotaDtos.QuotaResponse> data = quotas.dayRows(day).stream()
                .map(QuotaDtos.QuotaResponse::from)
                .toList();
        return new QuotaDtos.QuotaListResponse(data);
    }

    @PostMapping("/{quotaId}/adjust")
    @PreAuthorize("hasAnyRole('GATE_SUPERVISOR','ADMIN')")
    public ResponseEntity<QuotaDtos.AdjustQuotaResponse> adjust(
            @PathVariable String quotaId,
            @RequestBody QuotaDtos.AdjustQuotaRequest request,
            Authentication authentication) {
        if (request.newMaxAppointments() == null || request.newMaxAppointments() < 0) {
            throw new ApiException(422, "invalid_quota_cap", "newMaxAppointments is required and cannot be negative");
        }
        UUID adjustedBy = request.adjustedBy() != null ? request.adjustedBy() : operatorId(authentication);
        AppointmentQuota quota = quotas.adjust(GateIds.toUuid(quotaId, GateIds.QUOTA_PREFIX),
                request.newMaxAppointments(), adjustedBy, request.reason());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new QuotaDtos.AdjustQuotaResponse(QuotaDtos.QuotaResponse.from(quota)));
    }

    private UUID operatorId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            return UUID.nameUUIDFromBytes(authentication.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}