package com.smartportx.gate;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartportx.gate.domain.Appointment;
import com.smartportx.gate.domain.AppointmentStatus;
import com.smartportx.gate.domain.AppointmentType;
import com.smartportx.gate.domain.events.GateDomainEvents;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Appointment booking lifecycle (M6.2) over the real HTTP stack: book, preview,
 * confirm, cancel, update/reschedule, paginated listing and RBAC.
 */
@Transactional
class AppointmentIntegrationTest extends BaseGateIntegrationTest {

    private static final Instant SLOT = Instant.parse("2026-06-08T06:00:00Z");

    private Map<String, Object> bookRequest(String type) {
        return Map.of(
                "truckingCompany", "FreightWorks",
                "truckLicensePlate", "ABC-1234",
                "driverName", "John Doe",
                "driverLicense", "DL-1234567",
                "appointmentType", type,
                "containerNumber", "MSKU1234567",
                "preferredTimeSlot", SLOT.toString(),
                "durationMinutes", 30);
    }

    @Test
    void book_createsConfirmedAppointmentAndPublishesEvent() throws Exception {
        doPost("/api/v1/appointments", json(bookRequest("PICKUP")), GateTestTokens.gateOperator())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.appointmentType").value("PICKUP"))
                .andExpect(jsonPath("$.confirmationNumber", startsWith("APT-")))
                .andExpect(jsonPath("$.documents[0]").value("BILL_OF_LADING"))
                .andExpect(jsonPath("$.documents[1]").value("DELIVERY_ORDER"))
                .andExpect(jsonPath("$.estimatedCost").value(50000))
                .andExpect(jsonPath("$.availableSlots").value(24));

        GateDomainEvents.AppointmentBookedPayload payload = payloadOfType(
                GateDomainEvents.APPOINTMENT_BOOKED, GateDomainEvents.AppointmentBookedPayload.class)
                .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(payload.confirmationNumber()).startsWith("APT-");
        org.assertj.core.api.Assertions.assertThat(payload.preferredTimeSlot()).isEqualTo(SLOT);
    }

    @Test
    void book_rejectsOutsideOperatingHours() throws Exception {
        String slot = Instant.parse("2026-06-08T05:00:00Z").toString();
        doPost("/api/v1/appointments", json(Map.of(
                "truckingCompany", "FreightWorks", "truckLicensePlate", "ABC-1234",
                "driverName", "John Doe", "driverLicense", "DL-1234567",
                "appointmentType", "DROP_OFF", "containerNumber", "MSKU1234567",
                "preferredTimeSlot", slot)), GateTestTokens.gateOperator())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("appointment_invalid_time_slot"));
    }

    @Test
    void book_rejectsSlotBelowMinimumLead() throws Exception {
        freezeNow(Instant.parse("2026-06-08T05:00:00Z"));
        doPost("/api/v1/appointments", json(bookRequest("DROP_OFF")), GateTestTokens.gateOperator())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("appointment_invalid_time_slot"));
    }

    @Test
    void book_rejectsSlotBeyondBookAheadHorizon() throws Exception {
        Map<String, Object> request = new java.util.LinkedHashMap<>(bookRequest("DROP_OFF"));
        request.put("preferredTimeSlot", Instant.parse("2026-06-20T06:00:00Z").toString());
        doPost("/api/v1/appointments", json(request), GateTestTokens.gateOperator())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("appointment_invalid_time_slot"));
    }

    @Test
    void preview_returnsEstimateAndAvailability() throws Exception {
        stubContainer(container("IN_YARD", 20000.0));
        doPost("/api/v1/appointments/preview", json(Map.of(
                "appointmentType", "PICKUP",
                "preferredTimeSlot", SLOT.toString(),
                "containerNumber", "MSKU1234567")), GateTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentType").value("PICKUP"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.availableSlots").value(25))
                .andExpect(jsonPath("$.estimatedCost").value(50000))
                .andExpect(jsonPath("$.confirmationNumber", startsWith("APT-20260601-")));
    }

    @Test
    void preview_guessesDropOffWhenContainerUnknown() throws Exception {
        stubContainerAbsent();
        doPost("/api/v1/appointments/preview", json(Map.of(
                "preferredTimeSlot", SLOT.toString(),
                "containerNumber", "MSKU9999999")), GateTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentType").value("DROP_OFF"));
    }

    @Test
    void confirm_pendingBecomesConfirmedAndPublishesEvent() throws Exception {
        Appointment pending = makeAppointment(AppointmentType.PICKUP, SLOT, AppointmentStatus.PENDING);
        doPost("/api/v1/appointments/" + "apt-" + pending.getId() + "/confirm", "{}",
                GateTestTokens.gateOperator())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.id").value(pending.getId().toString()));

        org.assertj.core.api.Assertions.assertThat(payloadOfType(
                GateDomainEvents.APPOINTMENT_CONFIRMED, GateDomainEvents.AppointmentConfirmedPayload.class))
                .isPresent();
    }

    @Test
    void confirm_alreadyConfirmed_conflict() throws Exception {
        Appointment confirmed = makeAppointment(AppointmentType.DROP_OFF, SLOT);
        doPost("/api/v1/appointments/" + "apt-" + confirmed.getId() + "/confirm", "{}",
                GateTestTokens.gateOperator())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("appointment_not_active"));
    }

    @Test
    void cancel_releasesQuotaAndPublishesCancelled() throws Exception {
        Appointment booked = makeAppointment(AppointmentType.DROP_OFF, SLOT);
        doPost("/api/v1/appointments/" + "apt-" + booked.getId() + "/cancel",
                json(Map.of("reason", "change of plans")), GateTestTokens.gateOperator())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        org.assertj.core.api.Assertions.assertThat(payloadOfType(
                GateDomainEvents.APPOINTMENT_CANCELLED, GateDomainEvents.AppointmentCancelledPayload.class))
                .isPresent();
    }

    @Test
    void update_reschedulesAndPublishesRescheduled() throws Exception {
        Appointment booked = makeAppointment(AppointmentType.DROP_OFF, SLOT);
        Instant newSlot = Instant.parse("2026-06-08T07:00:00Z");
        doPut("/api/v1/appointments/" + "apt-" + booked.getId(),
                json(Map.of("preferredTimeSlot", newSlot.toString())), GateTestTokens.gateOperator())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredTimeSlot").value(newSlot.toString()));

        GateDomainEvents.AppointmentRescheduledPayload payload = payloadOfType(
                GateDomainEvents.APPOINTMENT_RESCHEDULED, GateDomainEvents.AppointmentRescheduledPayload.class)
                .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(payload.previousTimeSlot()).isEqualTo(SLOT);
        org.assertj.core.api.Assertions.assertThat(payload.newTimeSlot()).isEqualTo(newSlot);
    }

    @Test
    void update_plateOnlyDoesNotReschedule() throws Exception {
        Appointment booked = makeAppointment(AppointmentType.DROP_OFF, SLOT);
        doPut("/api/v1/appointments/" + "apt-" + booked.getId(),
                json(Map.of("truckLicensePlate", "NEW-9999")), GateTestTokens.gateOperator())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truckLicensePlate").value("NEW-9999"));

        org.assertj.core.api.Assertions.assertThat(hasEvent(GateDomainEvents.APPOINTMENT_RESCHEDULED)).isFalse();
    }

    @Test
    void list_supportsOpaqueCursorPagination() throws Exception {
        makeAppointment(AppointmentType.PICKUP, Instant.parse("2026-06-08T06:00:00Z"));
        makeAppointment(AppointmentType.PICKUP, Instant.parse("2026-06-08T07:00:00Z"));
        makeAppointment(AppointmentType.DROP_OFF, Instant.parse("2026-06-08T08:00:00Z"));

        String token = GateTestTokens.reader();
        JsonNode page1 = callJson(doGet("/api/v1/appointments?limit=2", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.pagination.totalCount").value(3))
                .andExpect(jsonPath("$.pagination.hasMore").value(true)));
        String cursor = page1.path("pagination").path("nextCursor").asText();
        org.assertj.core.api.Assertions.assertThat(cursor).isNotBlank();

        callJson(doGet("/api/v1/appointments?limit=2&cursor=" + cursor, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.pagination.hasMore").value(false)));
    }

    @Test
    void list_filtersByStatus() throws Exception {
        makeAppointment(AppointmentType.DROP_OFF, SLOT);
        makeAppointment(AppointmentType.DROP_OFF, Instant.parse("2026-06-08T09:00:00Z"),
                AppointmentStatus.CANCELLED);
        doGet("/api/v1/appointments?filter[status]=CANCELLED", GateTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("CANCELLED"));
    }

    @Test
    void get_acceptsPrefixedAndBareId_rejectsGarbage() throws Exception {
        Appointment booked = makeAppointment(AppointmentType.PICKUP, SLOT);
        String id = "apt-" + booked.getId();
        doGet("/api/v1/appointments/" + id, GateTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentId").value(id));
        doGet("/api/v1/appointments/" + booked.getId(), GateTestTokens.reader())
                .andExpect(status().isOk());
        doGet("/api/v1/appointments/not-a-uuid", GateTestTokens.reader())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
    }

    @Test
    void book_requiresAuthenticationAndGateRole() throws Exception {
        String body = json(bookRequest("DROP_OFF"));
        doPost("/api/v1/appointments", body, null)
                .andExpect(status().isUnauthorized());
        doPost("/api/v1/appointments", body, GateTestTokens.reader())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    @Test
    void reads_workForAnyAuthenticatedUser() throws Exception {
        makeAppointment(AppointmentType.DROP_OFF, SLOT);
        doGet("/api/v1/appointments", GateTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void list_rejectsMalformedFilter() throws Exception {
        doGet("/api/v1/appointments?filter[type]=TELEPORT", GateTestTokens.reader())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }
}