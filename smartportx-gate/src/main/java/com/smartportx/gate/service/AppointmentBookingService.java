package com.smartportx.gate.service;

import com.smartportx.common.error.ApiException;
import com.smartportx.common.error.NotFoundException;
import com.smartportx.gate.config.GateProperties;
import com.smartportx.gate.domain.Appointment;
import com.smartportx.gate.domain.AppointmentQuota;
import com.smartportx.gate.domain.AppointmentStatus;
import com.smartportx.gate.domain.AppointmentType;
import com.smartportx.gate.repository.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Appointment booking, preview, confirmation, reschedule and cancellation (M6.2).
 * Every mutable operation reserves/releases quota capacity so a window can never
 * overbook (Race-condition safe via {@link QuotaService} pessimistic locking).
 */
@Service
public class AppointmentBookingService {

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private final AppointmentRepository appointments;
    private final QuotaService quotas;
    private final SequenceNumbers numbers;
    private final GateClock clock;
    private final GateProperties properties;
    private final GateEventLogger events;
    private final ContainerClient containerClient;

    public AppointmentBookingService(AppointmentRepository appointments,
                                     QuotaService quotas,
                                     SequenceNumbers numbers,
                                     GateClock clock,
                                     GateProperties properties,
                                     GateEventLogger events,
                                     ContainerClient containerClient) {
        this.appointments = appointments;
        this.quotas = quotas;
        this.numbers = numbers;
        this.clock = clock;
        this.properties = properties;
        this.events = events;
        this.containerClient = containerClient;
    }

    @Transactional
    public Appointment book(BookCommand command) {
        assertType(command.appointmentType());
        quotas.assertInsideOperatingHours(command.preferredTimeSlot());
        assertNotInPast(command.preferredTimeSlot());
        quotas.reserve(command.preferredTimeSlot(), command.appointmentType().quotaType());

        Appointment appointment = new Appointment();
        appointment.setConfirmationNumber(numbers.nextConfirmationNumber());
        appointment.setTruckingCompany(command.truckingCompany());
        appointment.setTruckLicensePlate(command.truckLicensePlate());
        appointment.setDriverName(command.driverName());
        appointment.setDriverLicense(command.driverLicense());
        appointment.setAppointmentType(command.appointmentType());
        appointment.setContainerNumber(normalizeContainerNumber(command.containerNumber()));
        appointment.setPreferredTimeSlot(command.preferredTimeSlot());
        appointment.setDurationMinutes(command.durationMinutes() != null && command.durationMinutes() > 0
                ? command.durationMinutes() : 30);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setGateLane(command.gateLane());
        appointment.setSpecialRequirements(command.specialRequirements() == null || command.specialRequirements().isEmpty()
                ? null : command.specialRequirements());
        appointment.setNotes(command.notes());
        Appointment saved = appointments.save(appointment);

        events.appointmentBooked(saved.getId(), saved.getConfirmationNumber(),
                saved.getAppointmentType().name(), saved.getContainerNumber(), saved.getPreferredTimeSlot());
        return saved;
    }

    /** Do-not-persist booking feasibility + quote. */
    public PreviewResult preview(AppointmentType requestedType, Instant slot, String containerNumber) {
        quotas.assertInsideOperatingHours(slot);
        AppointmentType type = requestedType != null ? requestedType : guessType(containerNumber);
        int available = availableSlots(type, slot);
        return new PreviewResult(type, true, slot, numbers.nextConfirmationNumber(),
                available, properties.getBookingEstimateFee());
    }

    @Transactional
    public Appointment confirm(UUID appointmentId) {
        Appointment appointment = appointment(appointmentId);
        if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
            throw new ApiException(409, "appointment_not_active", "Appointment is already confirmed");
        }
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new ApiException(409, "appointment_not_active",
                    "Only a PENDING appointment can be confirmed (current: " + appointment.getStatus() + ")");
        }
        quotas.assertInsideOperatingHours(appointment.getPreferredTimeSlot());
        quotas.reserve(appointment.getPreferredTimeSlot(), appointment.getAppointmentType().quotaType());
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        Appointment saved = appointments.save(appointment);
        events.appointmentConfirmed(saved.getId(), saved.getConfirmationNumber(), saved.getPreferredTimeSlot());
        return saved;
    }

    @Transactional
    public Appointment update(UUID appointmentId, UpdateCommand command) {
        Appointment appointment = appointment(appointmentId);
        if (!appointment.getStatus().isModifiable()) {
            throw new ApiException(409, "appointment_not_active",
                    "Only a PENDING or CONFIRMED appointment can be changed (current: " + appointment.getStatus() + ")");
        }

        if (command.preferredTimeSlot() != null && !command.preferredTimeSlot().equals(appointment.getPreferredTimeSlot())) {
            quotas.assertInsideOperatingHours(command.preferredTimeSlot());
            assertNotInPast(command.preferredTimeSlot());
            AppointmentType type = appointment.getAppointmentType().quotaType();
            quotas.reserve(command.preferredTimeSlot(), type);
            quotas.release(appointment.getPreferredTimeSlot(), type);
            Instant previous = appointment.getPreferredTimeSlot();
            appointment.setPreferredTimeSlot(command.preferredTimeSlot());
            events.appointmentRescheduled(appointment.getId(), appointment.getConfirmationNumber(),
                    previous, command.preferredTimeSlot());
        }

        if (command.truckLicensePlate() != null) {
            appointment.setTruckLicensePlate(command.truckLicensePlate());
        }
        if (command.driverName() != null) {
            appointment.setDriverName(command.driverName());
        }
        if (command.driverLicense() != null) {
            appointment.setDriverLicense(command.driverLicense());
        }
        if (command.containerNumber() != null) {
            appointment.setContainerNumber(normalizeContainerNumber(command.containerNumber()));
        }
        if (command.gateLane() != null) {
            appointment.setGateLane(command.gateLane());
        }
        if (command.specialRequirements() != null) {
            appointment.setSpecialRequirements(command.specialRequirements().isEmpty() ? null : command.specialRequirements());
        }
        if (command.notes() != null) {
            appointment.setNotes(command.notes());
        }
        return appointments.save(appointment);
    }

    @Transactional
    public Appointment cancel(UUID appointmentId, String reason) {
        Appointment appointment = appointment(appointmentId);
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            return appointment;
        }
        if (appointment.getStatus() == AppointmentStatus.COMPLETED
                || appointment.getStatus() == AppointmentStatus.NO_SHOW) {
            throw new ApiException(422, "appointment_not_active",
                    "A completed or missed appointment cannot be cancelled (current: " + appointment.getStatus() + ")");
        }
        quotas.release(appointment.getPreferredTimeSlot(), appointment.getAppointmentType().quotaType());
        appointment.setStatus(AppointmentStatus.CANCELLED);
        Appointment saved = appointments.save(appointment);
        events.appointmentCancelled(saved.getId(), saved.getConfirmationNumber(), reason);
        return saved;
    }

    /** Lazy no-show marking on a failed late check-in; releases the reserved slot. */
    @Transactional
    public Appointment markNoShow(UUID appointmentId) {
        Appointment appointment = appointment(appointmentId);
        if (appointment.getStatus().isActive()) {
            quotas.release(appointment.getPreferredTimeSlot(), appointment.getAppointmentType().quotaType());
            appointment.setStatus(AppointmentStatus.NO_SHOW);
            appointment = appointments.save(appointment);
        }
        return appointment;
    }

    @Transactional(readOnly = true)
    public Appointment appointment(UUID appointmentId) {
        return appointments.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment not found"));
    }

    public int availableSlots(AppointmentType type, Instant slot) {
        AppointmentQuota quota = quotas.quotaFor(slot, type.quotaType(), "ALL");
        return quota.availableSlots();
    }

    public AppointmentType guessType(String containerNumber) {
        if (containerNumber == null || containerNumber.isBlank()) {
            return AppointmentType.DROP_OFF;
        }
        Optional<ContainerClient.ContainerInfo> container = containerClient.findByContainerNumber(containerNumber);
        if (container.isPresent()) {
            String status = container.get().status();
            if ("IN_YARD".equals(status) || "AT_GATE".equals(status)
                    || "AT_GATE_OUT".equals(status) || "DISCHARGED".equals(status)) {
                return AppointmentType.PICKUP;
            }
        }
        return AppointmentType.DROP_OFF;
    }

    private void assertType(AppointmentType type) {
        if (type == null) {
            throw new ApiException(400, "validation_failed", "appointmentType is required");
        }
    }

    private void assertNotInPast(Instant slot) {
        Instant earliest = clock.now().plus(Duration.ofHours(properties.getMinLeadHours()));
        if (!slot.isAfter(earliest)) {
            throw new ApiException(422, "appointment_invalid_time_slot",
                    "Preferred slot must be at least " + properties.getMinLeadHours()
                            + " hours ahead of now (lead time must be 1-14 days)");
        }
        Instant latest = clock.now().plus(java.time.Duration.ofDays(properties.getMaxBookAheadDays()));
        if (slot.isAfter(latest)) {
            throw new ApiException(422, "appointment_invalid_time_slot",
                    "Preferred slot cannot be booked more than " + properties.getMaxBookAheadDays() + " days ahead");
        }
    }

    /** Container number normalisation: accept the ISO 6346 form; trim/collapse spaces. */
    static String normalizeContainerNumber(String containerNumber) {
        if (containerNumber == null) {
            return null;
        }
        return containerNumber.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
    }

    /** Paged listing with contract-compliant cursor response (M6.2). */
    public ListResult list(ListRequest request) {
        List<Appointment> filtered = loadFiltered(request).stream()
                .sorted((a, b) -> {
                    int cmp = a.getPreferredTimeSlot().compareTo(b.getPreferredTimeSlot());
                    return request.sortDesc() ? -cmp : cmp;
                })
                .collect(Collectors.toList());

        int total = filtered.size();
        int from = Math.min(request.cursor(), filtered.size());
        int to = Math.min(from + request.limit(), filtered.size());
        List<Appointment> page = filtered.subList(from, to);
        int nextCursor = to < filtered.size() ? to : -1;
        return new ListResult(page, nextCursor > 0 ? String.valueOf(nextCursor) : null,
                nextCursor > 0, total, request.limit());
    }

    private List<Appointment> loadFiltered(ListRequest request) {
        Collection<AppointmentStatus> statuses = request.status() == null
                ? List.of(AppointmentStatus.values())
                : List.of(request.status());

        if (request.date() != null) {
            Instant from = request.date().atStartOfDay(UTC).toInstant();
            Instant to = request.date().plusDays(1).atStartOfDay(UTC).toInstant();
            if (request.type() != null) {
                return appointments.findByPreferredTimeSlotBetweenAndStatusIn(from, to, statuses).stream()
                        .filter(a -> a.getAppointmentType() == request.type())
                        .collect(Collectors.toList());
            }
            if (request.company() != null) {
                return appointments.findByPreferredTimeSlotBetweenAndStatusIn(from, to, statuses).stream()
                        .filter(a -> request.company().equalsIgnoreCase(a.getTruckingCompany()))
                        .collect(Collectors.toList());
            }
            return appointments.findByPreferredTimeSlotBetweenAndStatusIn(from, to, statuses);
        }
        if (request.type() != null && request.company() != null) {
            return appointments.findAllByStatusIn(statuses).stream()
                    .filter(a -> a.getAppointmentType() == request.type())
                    .filter(a -> request.company().equalsIgnoreCase(a.getTruckingCompany()))
                    .collect(Collectors.toList());
        }
        if (request.type() != null) {
            return appointments.findAllByAppointmentType(request.type()).stream()
                    .filter(a -> statuses.contains(a.getStatus()))
                    .collect(Collectors.toList());
        }
        if (request.company() != null) {
            return appointments.findAllByTruckingCompany(request.company()).stream()
                    .filter(a -> statuses.contains(a.getStatus()))
                    .collect(Collectors.toList());
        }
        return appointments.findAllByStatusIn(statuses);
    }

    public record BookCommand(String truckingCompany, String truckLicensePlate, String driverName,
                              String driverLicense, AppointmentType appointmentType, String containerNumber,
                              Instant preferredTimeSlot, Integer durationMinutes, String gateLane,
                              Map<String, Boolean> specialRequirements, String notes) {
    }

    public record UpdateCommand(Instant preferredTimeSlot, String truckLicensePlate, String driverName,
                                String driverLicense, String containerNumber, String gateLane,
                                Map<String, Boolean> specialRequirements, String notes) {
    }

    public record PreviewResult(AppointmentType appointmentType, boolean available, Instant suggestedSlot,
                                String confirmationNumber, int availableSlots, double estimatedCost) {
    }

    public record ListRequest(AppointmentStatus status, AppointmentType type, String company,
                              LocalDate date, Instant from, Instant to,
                              boolean sortDesc, int cursor, int limit) {
    }

    public record ListResult(List<Appointment> items, String nextCursor, boolean hasMore, int totalCount, int limit) {
    }
}