package com.smartportx.gate.service;

import com.smartportx.common.event.DomainEvent;
import com.smartportx.gate.domain.events.GateDomainEvents;
import com.smartportx.gate.event.EventTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class GateEventLogger {

    private final EventTransport transport;
    private final String source;
    private final GateClock clock;

    public GateEventLogger(EventTransport transport,
                           @Value("${smartportx.gate.source}") String source,
                           GateClock clock) {
        this.transport = transport;
        this.source = source;
        this.clock = clock;
    }

    public void appointmentBooked(UUID appointmentId, String confirmationNumber, String appointmentType,
                                  String containerNumber, Instant preferredTimeSlot) {
        GateDomainEvents.AppointmentBookedPayload payload = new GateDomainEvents.AppointmentBookedPayload(
                appointmentId, confirmationNumber, appointmentType, containerNumber,
                preferredTimeSlot, "CONFIRMED");
        transport.publish(DomainEvent.of(GateDomainEvents.APPOINTMENT_BOOKED, source, payload));
    }

    public void appointmentConfirmed(UUID appointmentId, String confirmationNumber, Instant preferredTimeSlot) {
        GateDomainEvents.AppointmentConfirmedPayload payload = new GateDomainEvents.AppointmentConfirmedPayload(
                appointmentId, confirmationNumber, preferredTimeSlot);
        transport.publish(DomainEvent.of(GateDomainEvents.APPOINTMENT_CONFIRMED, source, payload));
    }

    public void appointmentCancelled(UUID appointmentId, String confirmationNumber, String reason) {
        GateDomainEvents.AppointmentCancelledPayload payload = new GateDomainEvents.AppointmentCancelledPayload(
                appointmentId, confirmationNumber, reason);
        transport.publish(DomainEvent.of(GateDomainEvents.APPOINTMENT_CANCELLED, source, payload));
    }

    public void appointmentRescheduled(UUID appointmentId, String confirmationNumber,
                                       Instant previousTimeSlot, Instant newTimeSlot) {
        GateDomainEvents.AppointmentRescheduledPayload payload = new GateDomainEvents.AppointmentRescheduledPayload(
                appointmentId, confirmationNumber, previousTimeSlot, newTimeSlot);
        transport.publish(DomainEvent.of(GateDomainEvents.APPOINTMENT_RESCHEDULED, source, payload));
    }

    public void gateCheckIn(UUID transactionId, UUID appointmentId, String confirmationNumber,
                            String containerNumber, String gateLane, Instant checkedInAt) {
        GateDomainEvents.GateCheckInPayload payload = new GateDomainEvents.GateCheckInPayload(
                transactionId, appointmentId, confirmationNumber, containerNumber, gateLane,
                checkedInAt, "CHECKED_IN");
        transport.publish(DomainEvent.of(GateDomainEvents.GATE_CHECK_IN, source, payload));
    }

    public void gateCheckOut(UUID transactionId, UUID appointmentId, String confirmationNumber,
                             String containerNumber, String gateLane, Instant checkedOutAt, String eirNumber) {
        GateDomainEvents.GateCheckOutPayload payload = new GateDomainEvents.GateCheckOutPayload(
                transactionId, appointmentId, confirmationNumber, containerNumber, gateLane,
                checkedOutAt, eirNumber, "CHECKED_OUT");
        transport.publish(DomainEvent.of(GateDomainEvents.GATE_CHECK_OUT, source, payload));
    }

    public void gateQueueUpdated(Instant snapshotTime, int waitingTrucks, int pickupCount,
                                 int dropoffCount, Instant estimatedClearance) {
        GateDomainEvents.GateQueueUpdatedPayload payload = new GateDomainEvents.GateQueueUpdatedPayload(
                snapshotTime, waitingTrucks, pickupCount, dropoffCount, estimatedClearance);
        transport.publish(DomainEvent.of(GateDomainEvents.GATE_QUEUE_UPDATED, source, payload));
    }
}