package com.smartportx.gate.service;

import com.smartportx.common.error.ApiException;
import com.smartportx.common.error.NotFoundException;
import com.smartportx.gate.config.GateProperties;
import com.smartportx.gate.domain.Appointment;
import com.smartportx.gate.domain.AppointmentQuota;
import com.smartportx.gate.domain.AppointmentStatus;
import com.smartportx.gate.domain.AppointmentType;
import com.smartportx.gate.domain.GateTransaction;
import com.smartportx.gate.repository.AppointmentRepository;
import com.smartportx.gate.repository.GateTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Gate check-in and check-out processing (M6.3/M6.5/M6.6). Cross-service validation
 * is fail-secure: an unreachable container/yard/customs provider blocks the crossing
 * rather than guessing. Arrival policy: on-time within the +/-15 minute window;
 * early = rejected; late inside the grace window = auto-reschedule to the next
 * window with capacity; beyond grace = appointment marked NO_SHOW.
 */
@Service
public class GateProcessingService {

    private static final Logger log = LoggerFactory.getLogger(GateProcessingService.class);

    private final AppointmentRepository appointments;
    private final GateTransactionRepository transactions;
    private final ContainerClient containerClient;
    private final YardClient yardClient;
    private final CustomsClient customsClient;
    private final QuotaService quotas;
    private final GateQueueService queueService;
    private final SequenceNumbers numbers;
    private final GateProperties properties;
    private final GateClock clock;
    private final GateEventLogger events;

    public GateProcessingService(AppointmentRepository appointments,
                                 GateTransactionRepository transactions,
                                 ContainerClient containerClient,
                                 YardClient yardClient,
                                 CustomsClient customsClient,
                                 QuotaService quotas,
                                 GateQueueService queueService,
                                 SequenceNumbers numbers,
                                 GateProperties properties,
                                 GateClock clock,
                                 GateEventLogger events) {
        this.appointments = appointments;
        this.transactions = transactions;
        this.containerClient = containerClient;
        this.yardClient = yardClient;
        this.customsClient = customsClient;
        this.quotas = quotas;
        this.queueService = queueService;
        this.numbers = numbers;
        this.properties = properties;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public CheckInResult checkIn(UUID appointmentId, CheckInCommand command, String verifiedBy) {
        Appointment appointment = appointments.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment not found"));

        switch (appointment.getStatus()) {
            case PENDING -> throw new ApiException(409, "appointment_not_active",
                    "Appointment must be confirmed before check-in");
            case CANCELLED -> throw new ApiException(409, "appointment_not_active",
                    "Appointment has been cancelled");
            case NO_SHOW -> throw new ApiException(422, "appointment_no_show",
                    "Appointment was marked as no-show; a new booking is required");
            case CHECKED_IN, IN_PROGRESS, COMPLETED -> throw new ApiException(409, "already_checked_in",
                    "This appointment has already been checked in");
            default -> {
            }
        }

        Instant now = clock.now();
        AppointmentType type = appointment.getAppointmentType();

        Instant windowStart = appointment.windowStart();
        Instant windowEnd = appointment.windowEnd();
        if (now.isBefore(windowStart)) {
            throw new ApiException(409, "appointment_not_in_window",
                    "Truck arrived too early; the check-in window opens at " + windowStart,
                    Map.of("windowStart", windowStart.toString(), "windowEnd", windowEnd.toString()));
        }
        if (now.isAfter(windowEnd)) {
            if (now.isAfter(windowEnd.plusSeconds(properties.getGraceMinutes() * 60L))) {
                Appointment noShow = noShow(appointment);
                log.warn("Appointment {} ({}) marked NO_SHOW: arrival beyond grace window",
                        appointment.getId(), appointment.getConfirmationNumber());
                throw new ApiException(422, "appointment_no_show",
                        "Arrival is beyond the grace window; appointment " + noShow.getConfirmationNumber()
                                + " is marked NO_SHOW. Please book a new appointment.");
            }
            if (!autoRescheduleToEarliestAvailable(appointment, now)) {
                throw new ApiException(409, "appointment_late",
                        "Truck is late and no alternative appointment window has capacity; "
                                + "please reschedule manually",
                        Map.of("rescheduleAvailable", true));
            }
        }

        assertIdentityMatch(appointment, command.truckLicensePlate(), command.driverLicense(),
                command.containerNumber());
        Optional<ContainerClient.ContainerInfo> container = containerClient.findByContainerNumber(
                appointment.getContainerNumber());
        assertContainerGateInEligible(appointment, container);

        CustomsClient.CustomsControlStatus customs = customsClient.statusFor(appointment.getContainerNumber())
                .orElseThrow(() -> new ApiException(422, "customs_unavailable",
                        "Customs control status could not be confirmed; gate-in blocked"));
        if (type == AppointmentType.PICKUP && customs.isHeld()) {
            throw new ApiException(422, "container_held",
                    "Container is under a customs hold and cannot be picked up");
        }

        List<String> required = GateRules.requiredDocuments(type);
        Set<String> submitted = command.documents() == null ? Set.of() : Set.copyOf(command.documents());
        List<String> missing = required.stream().filter(d -> !submitted.contains(d)).toList();
        if (!missing.isEmpty()) {
            throw new ApiException(422, "missing_documents",
                    "Required documents missing: " + missing,
                    Map.of("required", required, "missing", missing));
        }

        if (type == AppointmentType.PICKUP && (command.sealNumber() == null || command.sealNumber().isBlank())) {
            throw new ApiException(422, "seal_required",
                    "An outbound container must present a seal number at check-in");
        }

        String lane = bestAvailableLane();
        GateTransaction transaction = new GateTransaction();
        transaction.setAppointmentId(appointment.getId());
        transaction.setGateType(GateRules.gateTypeFor(type));
        transaction.setTruckLicensePlate(appointment.getTruckLicensePlate());
        transaction.setDriverName(appointment.getDriverName());
        transaction.setDriverLicense(appointment.getDriverLicense());
        transaction.setContainerNumber(appointment.getContainerNumber());
        transaction.setSealNumber(command.sealNumber());
        transaction.setDocuments(verifiedDocuments(submitted));
        transaction.setCheckInTime(now);
        transaction.setGateLane(lane);
        transaction.setYardLocation(appointment.getYardLocation());
        transaction.setStatus(com.smartportx.gate.domain.GateTransactionStatus.CHECKED_IN);
        GateTransaction saved = transactions.save(transaction);

        appointment.setStatus(AppointmentStatus.CHECKED_IN);
        appointment.setActualArrival(now);
        appointment.setGateLane(lane);
        appointments.save(appointment);

        queueService.snapshotAndPublish();
        events.gateCheckIn(saved.getId(), appointment.getId(), appointment.getConfirmationNumber(),
                appointment.getContainerNumber(), lane, now);

        return new CheckInResult(saved.getId(), appointment.getId(), appointment.getConfirmationNumber(),
                "CHECKED_IN", lane, now, saved.getYardLocation(),
                now.plusSeconds(appointment.getDurationMinutes() * 60L),
                documentResults(submitted, verifiedBy), instructions(type, appointment.getYardLocation(), lane));
    }

    @Transactional
    public CheckOutResult checkOut(UUID transactionId, CheckOutCommand command, String verifiedBy) {
        GateTransaction transaction = transactions.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Gate transaction not found"));
        if (transaction.getStatus() == com.smartportx.gate.domain.GateTransactionStatus.CHECKED_OUT) {
            throw new ApiException(409, "already_checked_out",
                    "This truck has already checked out (" + transaction.getEirNumber() + ")");
        }

        Appointment appointment = appointments.findById(transaction.getAppointmentId())
                .orElseThrow(() -> new NotFoundException("Appointment not found"));
        AppointmentType type = appointment.getAppointmentType();

        if (!appointment.getTruckLicensePlate().equalsIgnoreCase(command.truckLicensePlate())) {
            throw new ApiException(403, "unauthorized_truck",
                    "Truck license plate does not match the booking");
        }
        if (command.driverLicense() != null && !appointment.getDriverLicense().equals(command.driverLicense())) {
            throw new ApiException(403, "unauthorized_truck", "Driver licence does not match the booking");
        }
        if (!AppointmentBookingService.normalizeContainerNumber(command.containerNumber())
                .equals(appointment.getContainerNumber())) {
            throw new ApiException(403, "unauthorized_truck", "Container does not match the booking");
        }

        Optional<Boolean> assigned = yardClient.isPositionAssigned(appointment.getContainerNumber());
        if (assigned.isEmpty()) {
            throw new ApiException(422, "yard_position_unconfirmed",
                    "Yard position status could not be confirmed; gate-out blocked");
        }
        if (type != AppointmentType.DROP_OFF && assigned.get()) {
            throw new ApiException(422, "container_not_retrieved",
                    "Container is still assigned a yard position; it must be retrieved (RTG) before gate-out");
        }
        if (type == AppointmentType.DROP_OFF && !assigned.get()) {
            throw new ApiException(422, "container_not_placed",
                    "Container has not been placed in the yard; gate-out before yard placement is not allowed");
        }

        CustomsClient.CustomsControlStatus customs = customsClient.statusFor(appointment.getContainerNumber())
                .orElseThrow(() -> new ApiException(422, "customs_unavailable",
                        "Customs control status could not be confirmed; gate-out blocked"));
        if (customs.isHeld()) {
            throw new ApiException(422, "container_held",
                    "Container is under a customs hold; gate-out is blocked");
        }

        if (Boolean.FALSE.equals(command.sealIntact())) {
            if (command.newSealNumber() == null || command.newSealNumber().isBlank()) {
                throw new ApiException(422, "seal_broken_unauthorized",
                        "A broken seal requires a replacement seal number to be declared");
            }
            transaction.setSealNumber(command.newSealNumber());
        }

        if (command.truckWeight() != null && type == AppointmentType.DROP_OFF) {
            Optional<ContainerClient.ContainerInfo> container =
                    containerClient.findByContainerNumber(appointment.getContainerNumber());
            if (container.isPresent() && container.get().weightKg() != null
                    && Math.abs(command.truckWeight() - container.get().weightKg())
                    > properties.getSwingWeightKg()) {
                throw new ApiException(422, "weight_discrepancy",
                        "Declared truck weight differs from the container weight by more than "
                                + properties.getSwingWeightKg() + " kg");
            }
        }

        Instant now = clock.now();
        transaction.setCheckOutTime(now);
        transaction.setStatus(com.smartportx.gate.domain.GateTransactionStatus.CHECKED_OUT);
        transaction.setEirNumber(numbers.nextEirNumber());
        GateTransaction saved = transactions.save(transaction);

        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointments.save(appointment);

        queueService.snapshotAndPublish();
        events.gateCheckOut(saved.getId(), appointment.getId(), appointment.getConfirmationNumber(),
                appointment.getContainerNumber(), saved.getGateLane(), now, saved.getEirNumber());

        return new CheckOutResult(saved.getId(), appointment.getId(), appointment.getConfirmationNumber(),
                "CHECKED_OUT", saved.getEirNumber(), saved.getCheckInTime(), now, saved.getGateLane(),
                saved.getYardLocation(), verifiedBy);
    }

    @Transactional
    public Appointment noShow(Appointment appointment) {
        quotas.release(appointment.getPreferredTimeSlot(), appointment.getAppointmentType().quotaType());
        appointment.setStatus(AppointmentStatus.NO_SHOW);
        return appointment;
    }

    private void assertIdentityMatch(Appointment appointment, String plate, String driverLicense,
                                     String containerNumber) {
        if (plate == null || !appointment.getTruckLicensePlate().equalsIgnoreCase(plate)) {
            throw new ApiException(403, "unauthorized_truck", "Truck license plate does not match the booking");
        }
        if (driverLicense == null || !appointment.getDriverLicense().equals(driverLicense)) {
            throw new ApiException(403, "unauthorized_truck", "Driver licence does not match the booking");
        }
        String normalized = AppointmentBookingService.normalizeContainerNumber(containerNumber);
        if (normalized == null || !appointment.getContainerNumber().equals(normalized)) {
            throw new ApiException(403, "unauthorized_truck", "Container does not match the booking");
        }
    }

    private void assertContainerGateInEligible(Appointment appointment,
                                               Optional<ContainerClient.ContainerInfo> container) {
        if (appointment.getAppointmentType() == AppointmentType.PICKUP) {
            if (container.isEmpty()) {
                throw new ApiException(404, "container_not_found",
                        "Container " + appointment.getContainerNumber() + " is not registered in the terminal");
            }
            ContainerClient.ContainerInfo info = container.get();
            if ("HELD".equals(info.status())) {
                throw new ApiException(422, "container_held",
                        "Container is under a customs hold and cannot be picked up");
            }
            if (!GateRules.PICKUPABLE_CONTAINER_STATES.contains(info.status())) {
                throw new ApiException(422, "container_not_available",
                        "Container state " + info.status() + " does not allow pickup");
            }
        }
    }

    /**
     * Late-but-within-grace arrival: try the next hourly windows (searching up to
     * {@code graceMinutes} ahead) that still have capacity; on success the appointment
     * is moved and a reschedule event is emitted.
     */
    private boolean autoRescheduleToEarliestAvailable(Appointment appointment, Instant now) {
        AppointmentType quotaType = appointment.getAppointmentType().quotaType();
        Instant cursor = now.truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS);
        Instant horizon = now.plusSeconds(properties.getGraceMinutes() * 60L);
        while (cursor.isBefore(horizon)) {
            if (quotas.isInsideOperatingHours(cursor)) {
                AppointmentQuota quota = quotas.quotaFor(cursor, quotaType, "ALL");
                if (quota.availableSlots() > 0) {
                    quotas.reserve(cursor, quotaType);
                    quotas.release(appointment.getPreferredTimeSlot(), quotaType);
                    Instant previous = appointment.getPreferredTimeSlot();
                    appointment.setPreferredTimeSlot(cursor);
                    appointments.save(appointment);
                    events.appointmentRescheduled(appointment.getId(), appointment.getConfirmationNumber(),
                            previous, cursor);
                    return true;
                }
            }
            cursor = cursor.plus(1, ChronoUnit.HOURS);
        }
        return false;
    }

    private String bestAvailableLane() {
        String best = null;
        int bestLoad = Integer.MAX_VALUE;
        for (String lane : properties.getActiveLanes()) {
            if (properties.getMaintenanceLanes().contains(lane)) {
                continue;
            }
            int load = transactions.findByStatusAndGateLane(
                    com.smartportx.gate.domain.GateTransactionStatus.CHECKED_IN, lane).size();
            if (load < bestLoad) {
                bestLoad = load;
                best = lane;
            }
        }
        return best == null ? "GATE-01" : best;
    }

    private Map<String, Boolean> verifiedDocuments(Set<String> submitted) {
        Map<String, Boolean> verified = new HashMap<>();
        for (String document : submitted) {
            verified.put(document, Boolean.TRUE);
        }
        return verified;
    }

    private List<DocumentResult> documentResults(Set<String> submitted, String verifiedBy) {
        List<DocumentResult> results = new ArrayList<>();
        for (String document : submitted) {
            results.add(new DocumentResult(document, displayName(document), "VERIFIED", clock.now(), verifiedBy));
        }
        return results;
    }

    private String instructions(AppointmentType type, String yardLocation, String lane) {
        String position = yardLocation == null
                ? (type == AppointmentType.PICKUP ? "the retrieval area" : "the assigned yard block")
                : "yard position " + yardLocation;
        return type == AppointmentType.DROP_OFF
                ? "Proceed to " + position + " via lane " + lane + " for container unloading."
                : "Proceed to " + position + " via lane " + lane + " for container loading.";
    }

    private static String displayName(String id) {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("DELIVERY_ORDER", "Delivery Order");
        names.put("BILL_OF_LADING", "Bill of Lading");
        names.put("CUSTOMS_RELEASE", "Customs Release");
        names.put("CUSTOMS_DECLARATION", "Customs Declaration");
        return names.getOrDefault(id, id);
    }

    public record CheckInCommand(String truckLicensePlate, String driverName, String driverLicense,
                                 String containerNumber, String sealNumber, Double truckWeight,
                                 List<String> documents, String notes) {
    }

    public record CheckInResult(UUID transactionId, UUID appointmentId, String confirmationNumber,
                                String status, String gateLane, Instant checkInTime, String yardLocation,
                                Instant estimatedServiceTime, List<DocumentResult> documents,
                                String instructions) {
    }

    public record CheckOutCommand(String truckLicensePlate, String driverName, String driverLicense,
                                  String containerNumber, Boolean sealIntact, String newSealNumber,
                                  Double truckWeight, String notes) {
    }

    public record CheckOutResult(UUID transactionId, UUID appointmentId, String confirmationNumber,
                                 String status, String eirNumber, Instant checkInTime, Instant checkOutTime,
                                 String gateLane, String yardLocation, String verifiedBy) {
    }

    public record DocumentResult(String id, String name, String status, Instant verifiedAt, String verifiedBy) {
    }
}