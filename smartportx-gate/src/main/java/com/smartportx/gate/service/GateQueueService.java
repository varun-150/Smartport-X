package com.smartportx.gate.service;

import com.smartportx.gate.config.GateProperties;
import com.smartportx.gate.domain.Appointment;
import com.smartportx.gate.domain.AppointmentStatus;
import com.smartportx.gate.domain.AppointmentType;
import com.smartportx.gate.domain.GateTransaction;
import com.smartportx.gate.domain.GateTransactionStatus;
import com.smartportx.gate.domain.QueueStatus;
import com.smartportx.gate.repository.AppointmentRepository;
import com.smartportx.gate.repository.GateTransactionRepository;
import com.smartportx.gate.repository.QueueStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gate queue tracking (M6.4). A {@link QueueStatus} snapshot is recalculated and
 * published after every gate transaction so downstream consumers receive a
 * transition-consistent view, and the live view is served on demand.
 */
@Service
public class GateQueueService {

    private final GateTransactionRepository transactions;
    private final AppointmentRepository appointments;
    private final QueueStatusRepository snapshots;
    private final GateEventLogger events;
    private final GateProperties properties;
    private final GateClock clock;

    public GateQueueService(GateTransactionRepository transactions,
                            AppointmentRepository appointments,
                            QueueStatusRepository snapshots,
                            GateEventLogger events,
                            GateProperties properties,
                            GateClock clock) {
        this.transactions = transactions;
        this.appointments = appointments;
        this.snapshots = snapshots;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public QueueStatus snapshotAndPublish() {
        List<GateTransaction> waiting = findWaiting();
        Map<AppointmentType, Long> byType = countByAppointmentType(waiting);
        long waitingCount = waiting.size();

        double previousAverage = snapshots.findFirstByOrderBySnapshotTimeDesc()
                .map(QueueStatus::getAvgWaitTimeMinutes)
                .filter(v -> v > 0.0)
                .orElse((double) properties.getAverageWaitMinutes());
        Instant estimatedClearance = waitingCount == 0 ? null
                : clock.now().plusSeconds(Math.round(previousAverage * waitingCount * 60L));

        QueueStatus status = new QueueStatus();
        status.setSnapshotTime(clock.now());
        status.setWaitingTrucks((int) waitingCount);
        status.setPickupCount(byType.getOrDefault(AppointmentType.PICKUP, 0L).intValue());
        status.setDropoffCount(byType.getOrDefault(AppointmentType.DROP_OFF, 0L).intValue()
                + byType.getOrDefault(AppointmentType.BOTH, 0L).intValue());
        status.setAvgWaitTimeMinutes(previousAverage);
        status.setEstimatedClearance(estimatedClearance);
        QueueStatus saved = snapshots.save(status);

        events.gateQueueUpdated(saved.getSnapshotTime(), saved.getWaitingTrucks(), saved.getPickupCount(),
                saved.getDropoffCount(), saved.getEstimatedClearance());
        return saved;
    }

    /** Live view served by GET /gate/queue (does not persist or republish). */
    @Transactional(readOnly = true)
    public QueueView currentView() {
        List<GateTransaction> waiting = findWaiting();
        Map<AppointmentType, Long> byType = countByAppointmentType(waiting);
        long waitingCount = waiting.size();

        List<LaneStatus> lanes = properties.getActiveLanes().stream()
                .map(lane -> {
                    String status = properties.getMaintenanceLanes().contains(lane) ? "MAINTENANCE" : "ACTIVE";
                    long current = waiting.stream().filter(t -> lane.equals(t.getGateLane())).count();
                    double wait = current == 0 ? 0.0 : (double) properties.getAverageWaitMinutes();
                    return new LaneStatus(lane, status, current, wait, wait * current);
                })
                .collect(Collectors.toList());

        List<AppointmentView> upcoming = appointments
                .findByPreferredTimeSlotGreaterThanEqualAndStatusInOrderByPreferredTimeSlotAsc(
                        clock.now(), List.of(AppointmentStatus.CONFIRMED))
                .stream()
                .limit(5)
                .map(a -> new AppointmentView(a.getConfirmationNumber(), a.getAppointmentType().name(),
                        a.getTruckLicensePlate(), a.getContainerNumber(), a.getPreferredTimeSlot()))
                .collect(Collectors.toList());

        return new QueueView((int) waitingCount,
                byType.getOrDefault(AppointmentType.PICKUP, 0L).intValue(),
                byType.getOrDefault(AppointmentType.DROP_OFF, 0L).intValue()
                        + byType.getOrDefault(AppointmentType.BOTH, 0L).intValue(),
                (double) properties.getAverageWaitMinutes(), lanes, upcoming, "gate-operator-1",
                List.of("Stay in your lane until the next booth opening",
                        "Have your appointment QR code and documents ready",
                        "Follow posted signage for yard navigation"));
    }

    private List<GateTransaction> findWaiting() {
        return transactions.findByStatusOrderByCheckInTimeAsc(GateTransactionStatus.CHECKED_IN);
    }

    private Map<AppointmentType, Long> countByAppointmentType(List<GateTransaction> waiting) {
        if (waiting.isEmpty()) {
            return Map.of();
        }
        Set<UUID> appointmentIds = waiting.stream().map(GateTransaction::getAppointmentId)
                .collect(Collectors.toSet());
        if (appointmentIds.isEmpty()) {
            return Map.of();
        }
        return appointments.findAllById(appointmentIds).stream()
                .collect(Collectors.groupingBy(Appointment::getAppointmentType, Collectors.counting()));
    }

    public record QueueView(int waitingTrucks, int pickupCount, int dropoffCount, double averageWaitMinutes,
                            List<LaneStatus> byGateLane, List<AppointmentView> upcomingAppointments,
                            String operator, List<String> instructions) {
    }

    public record LaneStatus(String lane, String status, long currentQueue, double averageWait,
                             double estimatedWaitMinutes) {
    }

    public record AppointmentView(String confirmationNumber, String type, String truckLicensePlate,
                                  String containerNumber, Instant preferredTimeSlot) {
    }
}