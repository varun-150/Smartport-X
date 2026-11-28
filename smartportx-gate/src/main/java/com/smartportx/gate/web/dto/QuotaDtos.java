package com.smartportx.gate.web.dto;

import com.smartportx.gate.domain.AppointmentQuota;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Appointment quota contracts (API-CONTRACTS &sect;6.4).
 */
public final class QuotaDtos {

    private QuotaDtos() {
    }

    public record QuotaResponse(String quotaId, LocalDate appointmentDate, Instant timeSlotStart,
                                Instant timeSlotEnd, String gateLane, String appointmentType,
                                int totalAppointments, int availableSlots, int reservedCount,
                                boolean dynamic, UUID adjustedBy, String adjustmentReason,
                                Instant updatedAt) {

        public static QuotaResponse from(AppointmentQuota quota) {
            return new QuotaResponse("quota-" + quota.getId(),
                    quota.getTimeWindowStart().atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                    quota.getTimeWindowStart(), quota.getTimeWindowEnd(), quota.getGateLane(),
                    quota.getAppointmentType().name(), quota.getMaxAppointments(),
                    quota.availableSlots(), quota.getCurrentBookings(), quota.isDynamicAdjustment(),
                    quota.getAdjustedBy(), quota.getAdjustmentReason(), quota.getUpdatedAt());
        }
    }

    public record QuotaListResponse(List<QuotaResponse> data) {
    }

    public record AdjustQuotaRequest(Integer newMaxAppointments, UUID adjustedBy, String reason) {
    }

    public record AdjustQuotaResponse(QuotaResponse quota) {
    }
}