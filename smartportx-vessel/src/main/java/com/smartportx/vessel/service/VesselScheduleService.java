package com.smartportx.vessel.service;

import com.smartportx.common.error.ApiException;
import com.smartportx.vessel.domain.SchedulePriority;
import com.smartportx.vessel.domain.Vessel;
import com.smartportx.vessel.domain.VesselSchedule;
import com.smartportx.vessel.domain.VesselScheduleStatus;
import com.smartportx.vessel.domain.events.VesselDomainEvents;
import com.smartportx.vessel.repository.VesselScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class VesselScheduleService {

    private static final Set<VesselScheduleStatus> TERMINAL =
            Set.of(VesselScheduleStatus.DEPARTED, VesselScheduleStatus.CANCELLED);

    private final VesselScheduleRepository schedules;
    private final VesselService vessels;
    private final VesselClock clock;
    private final VesselEventLogger events;

    public VesselScheduleService(VesselScheduleRepository schedules, VesselService vessels,
                                 VesselClock clock, VesselEventLogger events) {
        this.schedules = schedules;
        this.vessels = vessels;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public VesselSchedule add(UUID vesselId, String voyageNumber, String berthNumber,
                              Instant eta, Instant etd, SchedulePriority priority,
                              String portOfCall, String nextPort, Integer estimatedTeUs) {
        Vessel v = vessels.get(vesselId);
        VesselSchedule s = new VesselSchedule();
        s.setVesselId(v.getId());
        s.setVoyageNumber(voyageNumber);
        s.setBerthNumber(berthNumber);
        if (etd.isBefore(eta)) {
            throw new ApiException(422, "invalid_schedule", "etd must not precede eta");
        }
        s.setEta(eta);
        s.setEtd(etd);
        s.setPriority(priority == null ? SchedulePriority.MEDIUM : priority);
        s.setStatus(VesselScheduleStatus.SCHEDULED);
        s.setPortOfCall(portOfCall);
        s.setNextPort(nextPort);
        s.setEstimatedTeUs(estimatedTeUs);
        return schedules.save(s);
    }

    @Transactional
    public VesselSchedule get(UUID id) {
        return schedules.findById(id)
                .orElseThrow(() -> new ApiException(404, "schedule_not_found", "Schedule not found"));
    }

    @Transactional(readOnly = true)
    public List<VesselSchedule> byVessel(UUID vesselId) {
        vessels.get(vesselId);
        return schedules.findByVesselIdOrderByEtaDesc(vesselId);
    }

    @Transactional(readOnly = true)
    public List<VesselSchedule> inPort() {
        return schedules.findByStatusIn(VesselScheduleStatus.IN_PORT);
    }

    @Transactional(readOnly = true)
    public List<VesselSchedule> active() {
        return schedules.findByStatusIn(VesselScheduleStatus.ACTIVE);
    }

    @Transactional
    public VesselSchedule updateStatus(UUID scheduleId, VesselScheduleStatus target) {
        VesselSchedule s = get(scheduleId);
        transition(s, target);
        applySideEffects(s, target);
        return schedules.save(s);
    }

    private void transition(VesselSchedule s, VesselScheduleStatus target) {
        VesselScheduleStatus from = s.getStatus();
        if (target == from) {
            return;
        }
        if (TERMINAL.contains(from)) {
            throw new ApiException(409, "invalid_transition",
                    "Schedule is " + from + " and cannot change status");
        }
        boolean allowed =
                (from == VesselScheduleStatus.SCHEDULED && Set.of(
                        VesselScheduleStatus.IN_TRANSIT, VesselScheduleStatus.ARRIVED,
                        VesselScheduleStatus.CANCELLED).contains(target))
                || (from == VesselScheduleStatus.IN_TRANSIT && Set.of(
                        VesselScheduleStatus.ARRIVED, VesselScheduleStatus.CANCELLED).contains(target))
                || (from == VesselScheduleStatus.ARRIVED && Set.of(
                        VesselScheduleStatus.WORKING, VesselScheduleStatus.DEPARTED).contains(target))
                || (from == VesselScheduleStatus.WORKING && Set.of(
                        VesselScheduleStatus.DEPARTED).contains(target));
        if (!allowed) {
            throw new ApiException(409, "invalid_transition",
                    "Cannot move schedule from " + from + " to " + target);
        }
    }

    private void applySideEffects(VesselSchedule s, VesselScheduleStatus target) {
        Vessel v = vessels.get(s.getVesselId());
        Instant now = clock.now();
        switch (target) {
            case ARRIVED -> {
                s.setStatus(VesselScheduleStatus.ARRIVED);
                s.setArrivedAt(now);
                events.publish(VesselDomainEvents.VESSEL_ARRIVED,
                        new VesselDomainEvents.VesselArrivedPayload(
                                v.getId(), v.getVesselName(), v.getImoNumber(),
                                s.getVoyageNumber(), s.getBerthNumber(), now));
            }
            case DEPARTED -> {
                s.setStatus(VesselScheduleStatus.DEPARTED);
                s.setDepartedAt(now);
                events.publish(VesselDomainEvents.VESSEL_DEPARTED,
                        new VesselDomainEvents.VesselDepartedPayload(
                                v.getId(), v.getVesselName(), v.getImoNumber(),
                                s.getVoyageNumber(), now));
            }
            default -> s.setStatus(target);
        }
    }
}