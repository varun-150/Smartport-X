package com.smartportx.gate;

import com.smartportx.gate.domain.Appointment;
import com.smartportx.gate.domain.AppointmentQuota;
import com.smartportx.gate.domain.AppointmentStatus;
import com.smartportx.gate.domain.AppointmentType;
import com.smartportx.gate.domain.GateTransaction;
import com.smartportx.gate.domain.GateTransactionStatus;
import com.smartportx.gate.domain.events.GateDomainEvents;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gate check-in / check-out lifecycles (M6.3/M6.5/M6.6): window enforcement,
 * identity matching, provider fail-secure blocks, document/seal/weight rules,
 * late-arrival auto-reschedule and the no-show path.
 */
@Transactional
class CheckInOutIntegrationTest extends BaseGateIntegrationTest {

    private static final Instant SLOT = Instant.parse("2026-06-08T06:00:00Z");
    private static final List<String> PICKUP_DOCS = List.of("BILL_OF_LADING", "DELIVERY_ORDER");
    private static final List<String> DROP_OFF_DOCS = List.of("DELIVERY_ORDER");

    private Map<String, Object> checkInRequest(Appointment appointment, List<String> documents, String seal) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("appointmentId", "apt-" + appointment.getId());
        body.put("truckLicensePlate", appointment.getTruckLicensePlate());
        body.put("driverName", appointment.getDriverName());
        body.put("driverLicense", appointment.getDriverLicense());
        body.put("containerNumber", appointment.getContainerNumber());
        body.put("documents", documents);
        if (seal != null) {
            body.put("sealNumber", seal);
        }
        return body;
    }

    private Map<String, Object> checkOutRequest(String transactionId, Appointment appointment) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("transactionId", transactionId);
        body.put("truckLicensePlate", appointment.getTruckLicensePlate());
        body.put("driverName", appointment.getDriverName());
        body.put("driverLicense", appointment.getDriverLicense());
        body.put("containerNumber", appointment.getContainerNumber());
        body.put("sealIntact", true);
        return body;
    }

    private AppointmentQuota quotaAt(Instant slot, AppointmentType type, int max, int current) {
        AppointmentQuota quota = new AppointmentQuota();
        quota.setTimeWindowStart(slot.truncatedTo(java.time.temporal.ChronoUnit.HOURS));
        quota.setTimeWindowEnd(slot.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusSeconds(3600));
        quota.setAppointmentType(type);
        quota.setGateLane("ALL");
        quota.setMaxAppointments(max);
        quota.setCurrentBookings(current);
        return quotasRepo.saveAndFlush(quota);
    }

    private String checkIn(Appointment appointment, List<String> documents, String seal) throws Exception {
        return callJson(doPost("/api/v1/gate/check-in",
                json(checkInRequest(appointment, documents, seal)), GateTestTokens.gateOperator())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CHECKED_IN")))
                .path("transactionId").asText();
    }

    // ----- check-in -----

    @Test
    void checkIn_dropOff_happyPath() throws Exception {
        freezeNow(SLOT);
        stubCustoms("PENDING");
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);

        doPost("/api/v1/gate/check-in", json(checkInRequest(appointment, DROP_OFF_DOCS, null)),
                GateTestTokens.gateOperator())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId", startsWith("txn-")))
                .andExpect(jsonPath("$.gateLane").value("GATE-01"))
                .andExpect(jsonPath("$.documents.length()").value(1))
                .andExpect(jsonPath("$.instructions", org.hamcrest.Matchers.containsString("unloading")));

        org.assertj.core.api.Assertions.assertThat(
                appointments.findById(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CHECKED_IN);
        org.assertj.core.api.Assertions.assertThat(hasEvent(GateDomainEvents.GATE_CHECK_IN)).isTrue();
        org.assertj.core.api.Assertions.assertThat(hasEvent(GateDomainEvents.GATE_QUEUE_UPDATED)).isTrue();
    }

    @Test
    void checkIn_pickup_happyPath() throws Exception {
        freezeNow(SLOT);
        stubContainer(container("IN_YARD", null));
        stubCustoms("CLEARED");
        Appointment appointment = makeAppointment(AppointmentType.PICKUP, SLOT);

        doPost("/api/v1/gate/check-in", json(checkInRequest(appointment, PICKUP_DOCS, "SEAL-1")),
                GateTestTokens.gateOperator())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gateLane").value("GATE-01"))
                .andExpect(jsonPath("$.instructions", org.hamcrest.Matchers.containsString("loading")));

        org.assertj.core.api.Assertions.assertThat(
                appointments.findById(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CHECKED_IN);
    }

    @Test
    void checkIn_early_rejected() throws Exception {
        freezeNow(SLOT.minusSeconds(20 * 60));
        stubCustoms("PENDING");
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);

        doPost("/api/v1/gate/check-in", json(checkInRequest(appointment, DROP_OFF_DOCS, null)),
                GateTestTokens.gateOperator())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("appointment_not_in_window"));
    }

    @Test
    void checkIn_identityMismatch_forbidden() throws Exception {
        freezeNow(SLOT);
        stubCustoms("PENDING");
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);
        Map<String, Object> body = checkInRequest(appointment, DROP_OFF_DOCS, null);
        body.put("truckLicensePlate", "WRONG-000");

        doPost("/api/v1/gate/check-in", json(body), GateTestTokens.gateOperator())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("unauthorized_truck"));
    }

    @Test
    void checkIn_pickup_containerUnknown_notFound() throws Exception {
        freezeNow(SLOT);
        stubContainerAbsent();
        stubCustoms("CLEARED");
        Appointment appointment = makeAppointment(AppointmentType.PICKUP, SLOT);

        doPost("/api/v1/gate/check-in", json(checkInRequest(appointment, PICKUP_DOCS, "SEAL-1")),
                GateTestTokens.gateOperator())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("container_not_found"));
    }

    @Test
    void checkIn_pickup_containerHeld_blocked() throws Exception {
        freezeNow(SLOT);
        stubContainer(container("HELD", null));
        stubCustoms("CLEARED");
        Appointment appointment = makeAppointment(AppointmentType.PICKUP, SLOT);

        doPost("/api/v1/gate/check-in", json(checkInRequest(appointment, PICKUP_DOCS, "SEAL-1")),
                GateTestTokens.gateOperator())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("container_held"));
    }

    @Test
    void checkIn_pickup_containerNotAvailable_blocked() throws Exception {
        freezeNow(SLOT);
        stubContainer(container("IN_TRANSIT", null));
        stubCustoms("CLEARED");
        Appointment appointment = makeAppointment(AppointmentType.PICKUP, SLOT);

        doPost("/api/v1/gate/check-in", json(checkInRequest(appointment, PICKUP_DOCS, "SEAL-1")),
                GateTestTokens.gateOperator())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("container_not_available"));
    }

    @Test
    void checkIn_missingDocuments_blocked() throws Exception {
        freezeNow(SLOT);
        stubCustoms("PENDING");
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);

        doPost("/api/v1/gate/check-in", json(checkInRequest(appointment, List.of(), null)),
                GateTestTokens.gateOperator())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("missing_documents"));
    }

    @Test
    void checkIn_pickup_sealRequired_blocked() throws Exception {
        freezeNow(SLOT);
        stubContainer(container("IN_YARD", null));
        stubCustoms("CLEARED");
        Appointment appointment = makeAppointment(AppointmentType.PICKUP, SLOT);

        doPost("/api/v1/gate/check-in", json(checkInRequest(appointment, PICKUP_DOCS, null)),
                GateTestTokens.gateOperator())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("seal_required"));
    }

    @Test
    void checkIn_customsUnavailable_blocked() throws Exception {
        freezeNow(SLOT);
        stubCustomsUnknown();
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);

        doPost("/api/v1/gate/check-in", json(checkInRequest(appointment, DROP_OFF_DOCS, null)),
                GateTestTokens.gateOperator())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("customs_unavailable"));
    }

    @Test
    void checkIn_lateBeyondGrace_markedNoShow() throws Exception {
        freezeNow(SLOT.plusSeconds(150 * 60));
        stubCustoms("PENDING");
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);

        doPost("/api/v1/gate/check-in", json(checkInRequest(appointment, DROP_OFF_DOCS, null)),
                GateTestTokens.gateOperator())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("appointment_no_show"));

        org.assertj.core.api.Assertions.assertThat(
                appointments.findById(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.NO_SHOW);
        org.assertj.core.api.Assertions.assertThat(hasEvent(GateDomainEvents.GATE_CHECK_IN)).isFalse();
    }

    @Test
    void checkIn_lateWithinGrace_autoReschedulesAndChecksIn() throws Exception {
        freezeNow(SLOT.plusSeconds(30 * 60));
        stubCustoms("PENDING");
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);
        Instant rescheduled = Instant.parse("2026-06-08T07:00:00Z");

        doPost("/api/v1/gate/check-in", json(checkInRequest(appointment, DROP_OFF_DOCS, null)),
                GateTestTokens.gateOperator())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"));

        GateDomainEvents.AppointmentRescheduledPayload payload = payloadOfType(
                GateDomainEvents.APPOINTMENT_RESCHEDULED, GateDomainEvents.AppointmentRescheduledPayload.class)
                .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(payload.newTimeSlot()).isEqualTo(rescheduled);
    }

    @Test
    void checkIn_lateWithinGrace_noCapacity_lateConflict() throws Exception {
        freezeNow(SLOT.plusSeconds(30 * 60));
        stubCustoms("PENDING");
        quotaAt(Instant.parse("2026-06-08T07:00:00Z"), AppointmentType.DROP_OFF, 25, 25);
        quotaAt(Instant.parse("2026-06-08T08:00:00Z"), AppointmentType.DROP_OFF, 25, 25);
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);

        doPost("/api/v1/gate/check-in", json(checkInRequest(appointment, DROP_OFF_DOCS, null)),
                GateTestTokens.gateOperator())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("appointment_late"))
                .andExpect(jsonPath("$.rescheduleAvailable").value(true));

        org.assertj.core.api.Assertions.assertThat(
                appointments.findById(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void checkIn_alreadyCheckedIn_conflict() throws Exception {
        freezeNow(SLOT);
        stubCustoms("PENDING");
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);
        String body = json(checkInRequest(appointment, DROP_OFF_DOCS, null));

        doPost("/api/v1/gate/check-in", body, GateTestTokens.gateOperator())
                .andExpect(status().isCreated());
        doPost("/api/v1/gate/check-in", body, GateTestTokens.gateOperator())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("already_checked_in"));
    }

    @Test
    void checkIn_requiresAuthenticationAndOperatorRole() throws Exception {
        freezeNow(SLOT);
        stubCustoms("PENDING");
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);
        String body = json(checkInRequest(appointment, DROP_OFF_DOCS, null));

        doPost("/api/v1/gate/check-in", body, null)
                .andExpect(status().isUnauthorized());
        doPost("/api/v1/gate/check-in", body, GateTestTokens.reader())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    // ----- check-out -----

    @Test
    void checkOut_dropOff_happyPathWithEir() throws Exception {
        freezeNow(SLOT);
        stubCustoms("PENDING");
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);
        String txn = checkIn(appointment, DROP_OFF_DOCS, null);

        stubYard(true);
        stubCustoms("CLEARED");
        doPost("/api/v1/gate/check-out", json(checkOutRequest(txn, appointment)),
                GateTestTokens.gateOperator())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.eirNumber", startsWith("EIR-")));

        GateTransaction gateTransaction = transactions.findAll().stream()
                .filter(t -> t.getAppointmentId().equals(appointment.getId()))
                .findFirst().orElseThrow();
        org.assertj.core.api.Assertions.assertThat(gateTransaction.getStatus())
                .isEqualTo(GateTransactionStatus.CHECKED_OUT);
        org.assertj.core.api.Assertions.assertThat(
                appointments.findById(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.COMPLETED);
        org.assertj.core.api.Assertions.assertThat(hasEvent(GateDomainEvents.GATE_CHECK_OUT)).isTrue();
    }

    @Test
    void checkOut_pickup_afterRetrieval() throws Exception {
        freezeNow(SLOT);
        stubContainer(container("IN_YARD", null));
        stubCustoms("CLEARED");
        Appointment appointment = makeAppointment(AppointmentType.PICKUP, SLOT);
        String txn = checkIn(appointment, PICKUP_DOCS, "SEAL-1");

        stubYard(false);
        doPost("/api/v1/gate/check-out", json(checkOutRequest(txn, appointment)),
                GateTestTokens.gateOperator())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eirNumber", startsWith("EIR-")));
    }

    @Test
    void checkOut_pickup_containerNotRetrieved_blocked() throws Exception {
        freezeNow(SLOT);
        stubContainer(container("IN_YARD", null));
        stubCustoms("CLEARED");
        Appointment appointment = makeAppointment(AppointmentType.PICKUP, SLOT);
        String txn = checkIn(appointment, PICKUP_DOCS, "SEAL-1");

        stubYard(true);
        doPost("/api/v1/gate/check-out", json(checkOutRequest(txn, appointment)),
                GateTestTokens.gateOperator())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("container_not_retrieved"));
    }

    @Test
    void checkOut_dropOff_notPlaced_blocked() throws Exception {
        freezeNow(SLOT);
        stubCustoms("PENDING");
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);
        String txn = checkIn(appointment, DROP_OFF_DOCS, null);

        stubYard(false);
        doPost("/api/v1/gate/check-out", json(checkOutRequest(txn, appointment)),
                GateTestTokens.gateOperator())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("container_not_placed"));
    }

    @Test
    void checkOut_yardUnavailable_blocked() throws Exception {
        freezeNow(SLOT);
        stubCustoms("PENDING");
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);
        String txn = checkIn(appointment, DROP_OFF_DOCS, null);

        stubYardUnknown();
        doPost("/api/v1/gate/check-out", json(checkOutRequest(txn, appointment)),
                GateTestTokens.gateOperator())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("yard_position_unconfirmed"));
    }

    @Test
    void checkOut_customsHeld_blocked() throws Exception {
        freezeNow(SLOT);
        stubContainer(container("IN_YARD", null));
        stubCustoms("CLEARED");
        Appointment appointment = makeAppointment(AppointmentType.PICKUP, SLOT);
        String txn = checkIn(appointment, PICKUP_DOCS, "SEAL-1");

        stubYard(false);
        stubCustoms("HELD");
        doPost("/api/v1/gate/check-out", json(checkOutRequest(txn, appointment)),
                GateTestTokens.gateOperator())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("container_held"));
    }

    @Test
    void checkOut_weightDiscrepancy_blocked() throws Exception {
        freezeNow(SLOT);
        stubCustoms("PENDING");
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);
        String txn = checkIn(appointment, DROP_OFF_DOCS, null);

        stubContainer(container("IN_YARD", 20000.0));
        stubYard(true);
        Map<String, Object> body = checkOutRequest(txn, appointment);
        body.put("truckWeight", 100000);
        doPost("/api/v1/gate/check-out", json(body), GateTestTokens.gateOperator())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("weight_discrepancy"));
    }

    @Test
    void checkOut_sealBroken_requiresReplacementSeal() throws Exception {
        freezeNow(SLOT);
        stubCustoms("PENDING");
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);
        String txn = checkIn(appointment, DROP_OFF_DOCS, null);

        stubYard(true);
        Map<String, Object> body = checkOutRequest(txn, appointment);
        body.put("sealIntact", false);
        doPost("/api/v1/gate/check-out", json(body), GateTestTokens.gateOperator())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("seal_broken_unauthorized"));
    }

    @Test
    void checkOut_sealBroken_withReplacementSeal() throws Exception {
        freezeNow(SLOT);
        stubCustoms("PENDING");
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);
        String txn = checkIn(appointment, DROP_OFF_DOCS, null);

        stubYard(true);
        Map<String, Object> body = checkOutRequest(txn, appointment);
        body.put("sealIntact", false);
        body.put("newSealNumber", "SEAL-REPLACED");
        doPost("/api/v1/gate/check-out", json(body), GateTestTokens.gateOperator())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eirNumber", startsWith("EIR-")));
    }

    @Test
    void checkOut_requiresOperatorRole() throws Exception {
        freezeNow(SLOT);
        stubCustoms("PENDING");
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);
        String txn = checkIn(appointment, DROP_OFF_DOCS, null);
        String body = json(checkOutRequest(txn, appointment));

        doPost("/api/v1/gate/check-out", body, GateTestTokens.reader())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }
}