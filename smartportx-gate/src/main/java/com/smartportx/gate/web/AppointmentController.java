package com.smartportx.gate.web;

import com.smartportx.common.error.ApiException;
import com.smartportx.gate.config.GateProperties;
import com.smartportx.gate.domain.Appointment;
import com.smartportx.gate.domain.AppointmentStatus;
import com.smartportx.gate.domain.AppointmentType;
import com.smartportx.gate.service.AppointmentBookingService;
import com.smartportx.gate.support.GateIds;
import com.smartportx.gate.web.dto.AppointmentDtos;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private final AppointmentBookingService bookings;
    private final GateProperties properties;

    public AppointmentController(AppointmentBookingService bookings, GateProperties properties) {
        this.bookings = bookings;
        this.properties = properties;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public AppointmentDtos.AppointmentListResponse list(
            @RequestParam(name = "filter[status]", required = false) String status,
            @RequestParam(name = "filter[type]", required = false) String type,
            @RequestParam(name = "filter[company]", required = false) String company,
            @RequestParam(name = "filter[date]", required = false) String date,
            @RequestParam(defaultValue = "asc") String sort,
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(name = "cursor", required = false) String cursor) {

        AppointmentStatus statusValue = parse(status, AppointmentStatus.values(), "status");
        AppointmentType typeValue = parse(type, AppointmentType.values(), "type");
        LocalDate dateValue = date == null || date.isBlank() ? null : LocalDate.parse(date);
        int cursorValue = cursor == null || cursor.isBlank() ? 0 : parseCursor(cursor);
        int effectiveLimit = limit <= 0 ? 25 : Math.min(limit, 100);

        AppointmentBookingService.ListResult result = bookings.list(
                new AppointmentBookingService.ListRequest(statusValue, typeValue, company, dateValue,
                        null, null, "desc".equalsIgnoreCase(sort), cursorValue, effectiveLimit));

        List<AppointmentDtos.AppointmentResponse> data = new ArrayList<>();
        for (Appointment appointment : result.items()) {
            data.add(toResponse(appointment));
        }
        return new AppointmentDtos.AppointmentListResponse(data,
                new AppointmentDtos.Pagination(result.nextCursor(), result.hasMore(),
                        result.totalCount(), result.limit()));
    }

    @GetMapping("/{appointmentId}")
    @PreAuthorize("isAuthenticated()")
    public AppointmentDtos.AppointmentResponse get(@PathVariable String appointmentId) {
        Appointment appointment = bookings.appointment(GateIds.toUuid(appointmentId, GateIds.APPOINTMENT_PREFIX));
        return toResponse(appointment);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('GATE_OPERATOR','GATE_SUPERVISOR','ADMIN','TERMINAL_OP')")
    public ResponseEntity<AppointmentDtos.AppointmentResponse> book(
            @RequestBody AppointmentDtos.BookAppointmentRequest request) {
        Appointment appointment = bookings.book(toBookCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(appointment));
    }

    @PostMapping("/preview")
    @PreAuthorize("isAuthenticated()")
    public AppointmentDtos.PreviewResponse preview(
            @RequestBody AppointmentDtos.PreviewAppointmentRequest request) {
        AppointmentType type = parse(request.appointmentType(), AppointmentType.values(), "appointmentType");
        AppointmentBookingService.PreviewResult preview = bookings.preview(
                type, parseInstant(request.preferredTimeSlot()), request.containerNumber());
        return new AppointmentDtos.PreviewResponse(preview.appointmentType().name(), preview.available(),
                preview.suggestedSlot(), preview.confirmationNumber(), preview.availableSlots(),
                preview.estimatedCost());
    }

    @PostMapping("/{appointmentId}/confirm")
    @PreAuthorize("hasAnyRole('GATE_OPERATOR','GATE_SUPERVISOR','ADMIN','TERMINAL_OP')")
    public AppointmentDtos.AppointmentResponse confirm(@PathVariable String appointmentId,
                                                       @RequestBody(required = false)
                                                       AppointmentDtos.ConfirmAppointmentRequest request) {
        Appointment appointment = bookings.confirm(
                GateIds.toUuid(appointmentId, GateIds.APPOINTMENT_PREFIX));
        return toResponse(appointment);
    }

    @PostMapping("/{appointmentId}/cancel")
    @PreAuthorize("hasAnyRole('GATE_OPERATOR','GATE_SUPERVISOR','ADMIN','TERMINAL_OP')")
    public AppointmentDtos.AppointmentResponse cancel(@PathVariable String appointmentId,
                                                      @RequestBody(required = false)
                                                      AppointmentDtos.CancelAppointmentRequest request) {
        String reason = request == null ? null : request.reason();
        Appointment appointment = bookings.cancel(
                GateIds.toUuid(appointmentId, GateIds.APPOINTMENT_PREFIX), reason);
        return toResponse(appointment);
    }

    @PutMapping("/{appointmentId}")
    @PreAuthorize("hasAnyRole('GATE_OPERATOR','GATE_SUPERVISOR','ADMIN','TERMINAL_OP')")
    public AppointmentDtos.AppointmentResponse update(@PathVariable String appointmentId,
                                                      @RequestBody AppointmentDtos.UpdateAppointmentRequest request) {
        Appointment appointment = bookings.update(
                GateIds.toUuid(appointmentId, GateIds.APPOINTMENT_PREFIX), toUpdateCommand(request));
        return toResponse(appointment);
    }

    // ----- mapping helpers -----

    private AppointmentBookingService.BookCommand toBookCommand(AppointmentDtos.BookAppointmentRequest request) {
        return new AppointmentBookingService.BookCommand(
                request.truckingCompany(),
                request.truckLicensePlate(),
                request.driverName(),
                request.driverLicense(),
                parse(request.appointmentType(), AppointmentType.values(), "appointmentType"),
                request.containerNumber(),
                parseInstant(request.preferredTimeSlot()),
                request.durationMinutes(),
                request.gateLane(),
                toSpecialRequirements(request.specialRequirements()),
                request.notes());
    }

    private AppointmentBookingService.UpdateCommand toUpdateCommand(AppointmentDtos.UpdateAppointmentRequest request) {
        return new AppointmentBookingService.UpdateCommand(
                request.preferredTimeSlot() == null ? null : parseInstant(request.preferredTimeSlot()),
                request.truckLicensePlate(),
                request.driverName(),
                request.driverLicense(),
                request.containerNumber(),
                request.gateLane(),
                request.specialRequirements() == null ? null : toSpecialRequirements(request.specialRequirements()),
                request.notes());
    }

    private Map<String, Boolean> toSpecialRequirements(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (String value : values) {
            map.put(value, Boolean.TRUE);
        }
        return map;
    }

    private AppointmentDtos.AppointmentResponse toResponse(Appointment appointment) {
        int availableSlots = 0;
        try {
            availableSlots = bookings.availableSlots(appointment.getAppointmentType(),
                    appointment.getPreferredTimeSlot());
        } catch (RuntimeException ignored) {
            // availability is informational on read paths; never fail a read for it
        }
        return AppointmentDtos.AppointmentResponse.from(appointment,
                properties.getBookingEstimateFee(), availableSlots);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, "validation_failed", "preferredTimeSlot is required");
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            throw new ApiException(400, "validation_failed",
                    "preferredTimeSlot must be an ISO-8601 instant");
        }
    }

    private <E extends Enum<E>> E parse(String value, E[] values, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (E candidate : values) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new ApiException(400, "validation_failed", "Unknown " + field + " '" + value + "'");
    }

    private int parseCursor(String cursor) {
        try {
            int value = Integer.parseInt(cursor);
            return Math.max(0, value);
        } catch (NumberFormatException ex) {
            throw new ApiException(400, "malformed_request", "cursor must be a non-negative integer");
        }
    }
}