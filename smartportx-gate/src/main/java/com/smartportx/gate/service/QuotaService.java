package com.smartportx.gate.service;

import com.smartportx.common.error.ApiException;
import com.smartportx.common.error.NotFoundException;
import com.smartportx.gate.config.GateProperties;
import com.smartportx.gate.domain.AppointmentQuota;
import com.smartportx.gate.domain.AppointmentType;
import com.smartportx.gate.repository.AppointmentQuotaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Appointment quota capacity management (M6.2/M6.4, DATA-ARCHITECTURE &sect;8.4).
 * Quota windows are 60-minute buckets aligned to the hour (UTC). Rows are created
 * lazily on first touch so tests and cold starts are deterministic without a seeding
 * job. Reservations use a pessimistic DB lock on the row; concurrent bookers of the
 * same window are serialised.
 */
@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    private static final ZoneOffset UTC = ZoneOffset.UTC;
    private static final String ALL_LANES = "ALL";

    private final AppointmentQuotaRepository quotas;
    private final GateProperties properties;

    public QuotaService(AppointmentQuotaRepository quotas, GateProperties properties) {
        this.quotas = quotas;
        this.properties = properties;
    }

    public Instant windowStartFor(Instant slot) {
        return slot.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
    }

    public Instant windowEndFor(Instant slot) {
        return windowStartFor(slot).plusSeconds(3600);
    }

    public boolean isInsideOperatingHours(Instant slot) {
        return properties.getOperatingHoursStart().getHour() <= slot.atZone(UTC).getHour()
                && slot.atZone(UTC).getHour() < properties.getOperatingHoursEnd().getHour();
    }

    public void assertInsideOperatingHours(Instant slot) {
        if (!isInsideOperatingHours(slot)) {
            throw new ApiException(422, "appointment_invalid_time_slot",
                    "Preferred slot is outside gate operating hours "
                            + properties.getOperatingHoursStart() + "-" + properties.getOperatingHoursEnd());
        }
    }

    /** Load (creating if absent) the quota row for a slot/type/lane combination. */
    @Transactional
    public AppointmentQuota quotaFor(Instant slot, AppointmentType type, String gateLane) {
        Instant start = windowStartFor(slot);
        Instant end = windowEndFor(slot);
        String lane = gateLane == null ? ALL_LANES : gateLane;
        return quotas.findByTimeWindowStartAndTimeWindowEndAndAppointmentTypeAndGateLane(start, end, type, lane)
                .orElseGet(() -> {
                    AppointmentQuota created = new AppointmentQuota();
                    created.setTimeWindowStart(start);
                    created.setTimeWindowEnd(end);
                    created.setAppointmentType(type);
                    created.setGateLane(lane);
                    created.setMaxAppointments(properties.getDefaultWindowCapacity());
                    log.info("Quota: created window {} {} {} capacity {}",
                            start, end, type, properties.getDefaultWindowCapacity());
                    return quotas.save(created);
                });
    }

    /** Reserve one booking slot; throws 409 slot_unavailable when full. */
    @Transactional
    public void reserve(Instant slot, AppointmentType type) {
        AppointmentQuota quota = quotaFor(slot, type, ALL_LANES);
        if (quota.getCurrentBookings() >= quota.getMaxAppointments()) {
            throw new ApiException(409, "slot_unavailable",
                    "No appointment capacity remains in the " + quota.getTimeWindowStart() + " slot",
                    Map.of("windowStart", quota.getTimeWindowStart().toString(),
                            "availableSlots", 0,
                            "maxAppointments", quota.getMaxAppointments()));
        }
        quota.setCurrentBookings(quota.getCurrentBookings() + 1);
        quotas.save(quota);
    }

    /** Release a previously reserved booking slot (cancel / no-show / reschedule). */
    @Transactional
    public void release(Instant slot, AppointmentType type) {
        Instant start = windowStartFor(slot);
        Instant end = windowEndFor(slot);
        quotas.findByTimeWindowStartAndTimeWindowEndAndAppointmentTypeAndGateLane(start, end, type, ALL_LANES)
                .ifPresent(quota -> {
                    if (quota.getCurrentBookings() > 0) {
                        quota.setCurrentBookings(quota.getCurrentBookings() - 1);
                        quotas.save(quota);
                    }
                });
    }

    /** Ensure every operating-hour window of a day exists (both quota types). */
    @Transactional
    public void ensureDaySeeded(LocalDate day) {
        for (Instant start : operatingWindows(day)) {
            quotaFor(start, AppointmentType.PICKUP, ALL_LANES);
            quotaFor(start, AppointmentType.DROP_OFF, ALL_LANES);
        }
    }

    @Transactional(readOnly = true)
    public List<AppointmentQuota> dayRows(LocalDate day) {
        Instant from = day.atStartOfDay(UTC).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(UTC).toInstant();
        return quotas.findByTimeWindowStartBetweenOrderByTimeWindowStartAsc(from, to);
    }

    /** Dynamic capacity adjustment by a supervisor/admin. */
    @Transactional
    public AppointmentQuota adjust(UUID quotaId, int newMaxAppointments, UUID adjustedBy, String reason) {
        AppointmentQuota quota = quotas.findById(quotaId)
                .orElseThrow(() -> new NotFoundException("Quota window not found"));
        if (newMaxAppointments < quota.getCurrentBookings()) {
            throw new ApiException(422, "invalid_quota_cap",
                    "New maximum cannot be lower than current bookings (" + quota.getCurrentBookings() + ")");
        }
        quota.setMaxAppointments(newMaxAppointments);
        quota.setDynamicAdjustment(true);
        quota.setAdjustedBy(adjustedBy);
        quota.setAdjustmentReason(reason);
        log.info("Quota: {} adjusted max {} -> {} by {} ({})", quotaId, quota.getMaxAppointments(),
                newMaxAppointments, adjustedBy, reason);
        return quotas.save(quota);
    }

    private List<Instant> operatingWindows(LocalDate day) {
        List<Instant> windows = new ArrayList<>();
        int startHour = properties.getOperatingHoursStart().getHour();
        int endHour = properties.getOperatingHoursEnd().getHour();
        for (int hour = startHour; hour < endHour; hour++) {
            windows.add(day.atTime(hour, 0).atZone(UTC).toInstant());
        }
        return windows;
    }
}