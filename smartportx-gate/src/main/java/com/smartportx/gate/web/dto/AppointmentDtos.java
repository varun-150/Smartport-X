package com.smartportx.gate.web.dto;

import com.smartportx.gate.domain.Appointment;
import com.smartportx.gate.domain.AppointmentStatus;
import com.smartportx.gate.domain.AppointmentType;
import com.smartportx.gate.service.GateRules;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Appointment request/response contracts (API-CONTRACTS &sect;6). Wire ids are
 * prefixed ({@code apt-<uuid>}) per PHASE-4-DECISIONS D-446.
 */
public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    public record BookAppointmentRequest(String truckingCompany, String truckLicensePlate, String driverName,
                                         String driverLicense, String appointmentType, String containerNumber,
                                         String preferredTimeSlot, Integer durationMinutes, String gateLane,
                                         Boolean dangerousGoods, List<String> specialRequirements, String notes) {
    }

    public record PreviewAppointmentRequest(String appointmentType, String preferredTimeSlot,
                                            String containerNumber) {
    }

    public record ConfirmAppointmentRequest(String confirmationCode, String verifiedBy) {
    }

    public record CancelAppointmentRequest(String reason, String cancelledBy) {
    }

    public record UpdateAppointmentRequest(String preferredTimeSlot, String truckLicensePlate, String driverName,
                                           String driverLicense, String containerNumber, String gateLane,
                                           List<String> specialRequirements, String notes) {
    }

    public record AppointmentResponse(String id, String appointmentId, String confirmationNumber,
                                      String truckingCompany, String truckLicensePlate, String driverName,
                                      String driverLicense, String appointmentType, String containerNumber,
                                      Instant preferredTimeSlot, int durationMinutes, String status,
                                      String gateLane, String yardLocation, List<String> specialRequirements,
                                      List<String> documents, String notes, double estimatedCost,
                                      int availableSlots, Instant createdAt, Instant updatedAt) {

        public static AppointmentResponse from(Appointment appointment, double estimatedCost, int availableSlots) {
            return new AppointmentResponse(
                    appointment.getId() == null ? null : appointment.getId().toString(),
                    "apt-" + appointment.getId(),
                    appointment.getConfirmationNumber(),
                    appointment.getTruckingCompany(),
                    appointment.getTruckLicensePlate(),
                    appointment.getDriverName(),
                    appointment.getDriverLicense(),
                    appointment.getAppointmentType().name(),
                    appointment.getContainerNumber(),
                    appointment.getPreferredTimeSlot(),
                    appointment.getDurationMinutes(),
                    appointment.getStatus().name(),
                    appointment.getGateLane(),
                    appointment.getYardLocation(),
                    keys(appointment.getSpecialRequirements()),
                    GateRules.requiredDocuments(appointment.getAppointmentType()),
                    appointment.getNotes(),
                    estimatedCost,
                    availableSlots,
                    appointment.getCreatedAt(),
                    appointment.getUpdatedAt());
        }

        private static List<String> keys(Map<String, Boolean> map) {
            return map == null ? List.of() : List.copyOf(map.keySet());
        }
    }

    public record PreviewResponse(String appointmentType, boolean available, Instant suggestedSlot,
                                  String confirmationNumber, int availableSlots, double estimatedCost) {
    }

    public record AppointmentListResponse(List<AppointmentResponse> data, Pagination pagination) {
    }

    public record Pagination(String nextCursor, boolean hasMore, int totalCount, int limit) {
    }
}