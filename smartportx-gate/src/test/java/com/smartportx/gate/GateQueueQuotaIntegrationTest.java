package com.smartportx.gate;

import com.smartportx.gate.domain.Appointment;
import com.smartportx.gate.domain.AppointmentQuota;
import com.smartportx.gate.domain.AppointmentType;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gate queue snapshots (M6.4) and dynamic quota windows (M6.4) over the real
 * HTTP stack, including supervisor-only capacity adjustment.
 */
@Transactional
class GateQueueQuotaIntegrationTest extends BaseGateIntegrationTest {

    private static final Instant SLOT = Instant.parse("2026-06-08T06:00:00Z");

    private Appointment checkInDropOff() throws Exception {
        freezeNow(SLOT);
        stubCustoms("PENDING");
        Appointment appointment = makeAppointment(AppointmentType.DROP_OFF, SLOT);
        doPost("/api/v1/gate/check-in", json(Map.of(
                "appointmentId", "apt-" + appointment.getId(),
                "truckLicensePlate", appointment.getTruckLicensePlate(),
                "driverName", appointment.getDriverName(),
                "driverLicense", appointment.getDriverLicense(),
                "containerNumber", appointment.getContainerNumber(),
                "documents", List.of("DELIVERY_ORDER"))), GateTestTokens.gateOperator())
                .andExpect(status().isCreated());
        return appointment;
    }

    private Appointment checkInPickup() throws Exception {
        freezeNow(SLOT);
        stubContainer(container("IN_YARD", null));
        stubCustoms("CLEARED");
        Appointment appointment = makeAppointment(AppointmentType.PICKUP, SLOT);
        doPost("/api/v1/gate/check-in", json(Map.of(
                "appointmentId", "apt-" + appointment.getId(),
                "truckLicensePlate", appointment.getTruckLicensePlate(),
                "driverName", appointment.getDriverName(),
                "driverLicense", appointment.getDriverLicense(),
                "containerNumber", appointment.getContainerNumber(),
                "sealNumber", "SEAL-1",
                "documents", List.of("BILL_OF_LADING", "DELIVERY_ORDER"))), GateTestTokens.gateOperator())
                .andExpect(status().isCreated());
        return appointment;
    }

    private AppointmentQuota quotaRow(Instant slot, AppointmentType type, int max, int current) {
        AppointmentQuota quota = new AppointmentQuota();
        quota.setTimeWindowStart(slot.truncatedTo(java.time.temporal.ChronoUnit.HOURS));
        quota.setTimeWindowEnd(slot.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusSeconds(3600));
        quota.setAppointmentType(type);
        quota.setGateLane("ALL");
        quota.setMaxAppointments(max);
        quota.setCurrentBookings(current);
        return quotasRepo.saveAndFlush(quota);
    }

    @Test
    void queue_reflectsWaitingTrucksByTypeAndLane() throws Exception {
        checkInDropOff();
        checkInPickup();

        doGet("/api/v1/gate/queue", GateTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingTrucks").value(2))
                .andExpect(jsonPath("$.pickupCount").value(1))
                .andExpect(jsonPath("$.dropoffCount").value(1))
                .andExpect(jsonPath("$.byGateLane[0].lane").value("GATE-01"))
                .andExpect(jsonPath("$.byGateLane[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.byGateLane[0].currentQueue").value(1))
                .andExpect(jsonPath("$.byGateLane[1].lane").value("GATE-02"))
                .andExpect(jsonPath("$.byGateLane[1].currentQueue").value(1))
                .andExpect(jsonPath("$.upcomingAppointments").isArray());
    }

    @Test
    void queue_clearsAfterCheckOut() throws Exception {
        Appointment dropOff = checkInDropOff();
        checkInPickup();

        freezeNow(SLOT);
        stubYard(true);
        String transactionId = String.valueOf(transactions.findAll().stream()
                .filter(t -> t.getAppointmentId().equals(dropOff.getId()))
                .findFirst().orElseThrow().getId());
        doPost("/api/v1/gate/check-out", json(Map.of(
                "transactionId", "txn-" + transactionId,
                "truckLicensePlate", dropOff.getTruckLicensePlate(),
                "driverName", dropOff.getDriverName(),
                "driverLicense", dropOff.getDriverLicense(),
                "containerNumber", dropOff.getContainerNumber(),
                "sealIntact", true)), GateTestTokens.gateOperator())
                .andExpect(status().isCreated());

        doGet("/api/v1/gate/queue", GateTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingTrucks").value(1))
                .andExpect(jsonPath("$.pickupCount").value(1))
                .andExpect(jsonPath("$.dropoffCount").value(0));
    }

    @Test
    void queue_requiresAuthentication() throws Exception {
        doGet("/api/v1/gate/queue", null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void quotas_day_returnsLazyRowsOnly() throws Exception {
        doGet("/api/v1/quotas?date=2026-06-08", GateTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        doPost("/api/v1/appointments", json(Map.of(
                "truckingCompany", "FreightWorks",
                "truckLicensePlate", "ABC-1234",
                "driverName", "John Doe",
                "driverLicense", "DL-1234567",
                "appointmentType", "DROP_OFF",
                "containerNumber", "MSKU1234567",
                "preferredTimeSlot", SLOT.toString())), GateTestTokens.gateOperator())
                .andExpect(status().isCreated());

        doGet("/api/v1/quotas?date=2026-06-08", GateTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].appointmentType").value("DROP_OFF"))
                .andExpect(jsonPath("$.data[0].totalAppointments").value(25))
                .andExpect(jsonPath("$.data[0].reservedCount").value(1))
                .andExpect(jsonPath("$.data[0].availableSlots").value(24));
    }

    @Test
    void adjust_requiresSupervisorRoleAndAdjustsCapacity() throws Exception {
        AppointmentQuota quota = quotaRow(Instant.parse("2026-06-08T06:00:00Z"), AppointmentType.PICKUP, 25, 0);

        doPost("/api/v1/quotas/quota-" + quota.getId() + "/adjust",
                json(Map.of("newMaxAppointments", 40, "reason", "EURO 4 congestion event")),
                GateTestTokens.gateSupervisor())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quota.totalAppointments").value(40))
                .andExpect(jsonPath("$.quota.availableSlots").value(40))
                .andExpect(jsonPath("$.quota.dynamic").value(true));

        doGet("/api/v1/quotas?date=2026-06-08", GateTestTokens.gateSupervisor())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].dynamic").value(true));
    }

    @Test
    void adjust_belowCurrentBookings_rejected() throws Exception {
        AppointmentQuota quota = quotaRow(Instant.parse("2026-06-08T06:00:00Z"), AppointmentType.DROP_OFF, 5, 5);

        doPost("/api/v1/quotas/quota-" + quota.getId() + "/adjust",
                json(Map.of("newMaxAppointments", 3)), GateTestTokens.gateSupervisor())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("invalid_quota_cap"));
    }

    @Test
    void adjust_negativeCapAndReaderRole_rejected() throws Exception {
        AppointmentQuota quota = quotaRow(Instant.parse("2026-06-08T06:00:00Z"), AppointmentType.DROP_OFF, 25, 0);

        doPost("/api/v1/quotas/quota-" + quota.getId() + "/adjust",
                json(Map.of("newMaxAppointments", -1)), GateTestTokens.gateSupervisor())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("invalid_quota_cap"));

        doPost("/api/v1/quotas/quota-" + quota.getId() + "/adjust",
                json(Map.of("newMaxAppointments", 30)), GateTestTokens.reader())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }
}