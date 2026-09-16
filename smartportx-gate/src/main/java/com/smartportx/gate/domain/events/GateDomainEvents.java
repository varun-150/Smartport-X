package com.smartportx.gate.domain.events;

import java.time.Instant;
import java.util.UUID;

public final class GateDomainEvents {

    public static final String APPOINTMENT_BOOKED = "gate.AppointmentBooked";
    public static final String APPOINTMENT_CONFIRMED = "gate.AppointmentConfirmed";
    public static final String APPOINTMENT_CANCELLED = "gate.AppointmentCancelled";
    public static final String APPOINTMENT_RESCHEDULED = "gate.AppointmentRescheduled";
    public static final String GATE_CHECK_IN = "gate.GateCheckIn";
    public static final String GATE_QUEUE_UPDATED = "gate.GateQueueUpdated";
    public static final String GATE_CHECK_OUT = "gate.GateCheckOut";

    private GateDomainEvents() {
    }

    public record AppointmentBookedPayload(UUID appointmentId, String confirmationNumber,
                                           String appointmentType, String containerNumber,
                                           Instant preferredTimeSlot, String status) {
    }

    public record AppointmentConfirmedPayload(UUID appointmentId, String confirmationNumber,
                                              Instant preferredTimeSlot) {
    }

    public record AppointmentCancelledPayload(UUID appointmentId, String confirmationNumber, String reason) {
    }

    public record AppointmentRescheduledPayload(UUID appointmentId, String confirmationNumber,
                                                Instant previousTimeSlot, Instant newTimeSlot) {
    }

    public record GateCheckInPayload(UUID transactionId, UUID appointmentId, String confirmationNumber,
                                     String containerNumber, String gateLane, Instant checkedInAt,
                                     String status) {
    }

    public record GateCheckOutPayload(UUID transactionId, UUID appointmentId, String confirmationNumber,
                                      String containerNumber, String gateLane, Instant checkedOutAt,
                                      String eirNumber, String status) {
    }

    public record GateQueueUpdatedPayload(Instant snapshotTime, int waitingTrucks, int pickupCount,
                                          int dropoffCount, Instant estimatedClearance) {
    }
}