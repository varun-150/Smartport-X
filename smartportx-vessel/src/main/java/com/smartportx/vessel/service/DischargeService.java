package com.smartportx.vessel.service;

import com.smartportx.common.error.ApiException;
import com.smartportx.vessel.config.VesselProperties;
import com.smartportx.vessel.domain.DischargeContainer;
import com.smartportx.vessel.domain.DischargeContainerStatus;
import com.smartportx.vessel.domain.DischargePlan;
import com.smartportx.vessel.domain.DischargePlanStatus;
import com.smartportx.vessel.domain.Vessel;
import com.smartportx.vessel.domain.events.VesselDomainEvents;
import com.smartportx.vessel.repository.DischargeContainerRepository;
import com.smartportx.vessel.repository.DischargePlanRepository;
import com.smartportx.vessel.repository.VesselScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DischargeService {

    public static final String PRIORITY_NORMAL = "NORMAL";
    public static final String PRIORITY_HIGH = "HIGH";

    private final DischargePlanRepository plans;
    private final DischargeContainerRepository containers;
    private final VesselService vessels;
    private final VesselScheduleRepository schedules;
    private final ContainerApiClient containerClient;
    private final YardApiClient yardClient;
    private final VesselClock clock;
    private final VesselEventLogger events;
    private final VesselProperties properties;

    public DischargeService(DischargePlanRepository plans, DischargeContainerRepository containers,
                            VesselService vessels, VesselScheduleRepository schedules,
                            ContainerApiClient containerClient, YardApiClient yardClient,
                            VesselClock clock, VesselEventLogger events, VesselProperties properties) {
        this.plans = plans;
        this.containers = containers;
        this.vessels = vessels;
        this.schedules = schedules;
        this.containerClient = containerClient;
        this.yardClient = yardClient;
        this.clock = clock;
        this.events = events;
        this.properties = properties;
    }

    public static DischargeContainerStatus parseContainerStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(422, "invalid_status", "Container discharge status is required");
        }
        return switch (value.toUpperCase()) {
            case "DISCHARGED" -> DischargeContainerStatus.DISCHARGED;
            case "IN_TRANSIT", "DISCHARGING" -> DischargeContainerStatus.IN_TRANSIT;
            case "STACKED" -> DischargeContainerStatus.STACKED;
            case "MISROUTED" -> DischargeContainerStatus.MISROUTED;
            default -> throw new ApiException(422, "invalid_status",
                    "Unsupported container status: " + value);
        };
    }

    @Transactional
    public DischargePlan create(UUID vesselId, String voyageNumber,
                                List<ContainerLine> lines) {
        Vessel v = vessels.get(vesselId);
        DischargePlan plan = new DischargePlan();
        plan.setVesselId(v.getId());
        plan.setVoyageNumber(voyageNumber);
        var activeSchedule = schedules.findByVesselIdAndStatusIn(v.getId(),
                        com.smartportx.vessel.domain.VesselScheduleStatus.IN_PORT)
                .stream().findFirst().orElse(null);
        if (activeSchedule != null) {
            plan.setScheduleId(activeSchedule.getId());
        }
        plan.setStatus(DischargePlanStatus.PLANNED);
        plan = plans.save(plan);

        int teus = 0;
        for (ContainerLine line : lines) {
            if (containers.findByVesselAndContainer(vesselId, line.containerNumber()).stream()
                    .anyMatch(c -> c.getStatus() == DischargeContainerStatus.IN_TRANSIT
                            || c.getStatus() == DischargeContainerStatus.STACKED)) {
                throw new ApiException(409, "container_already_discharged",
                        "Container " + line.containerNumber() + " is already discharged on this vessel");
            }
            DischargeContainer c = new DischargeContainer();
            c.setDischargePlanId(plan.getId());
            c.setContainerNumber(line.containerNumber());
            c.setContainerType(line.containerType());
            c.setWeightKg(line.weightKg());
            c.setVesselPosition(line.vesselPosition());
            c.setYardTarget(line.yardTarget());
            c.setPriority(line.priority() == null ? PRIORITY_NORMAL : line.priority());
            c.setDangerousGoods(line.dangerousGoods());
            c.setReefer(line.reefer());
            c.setStatus(DischargeContainerStatus.PLANNED);
            containers.save(c);
            teus += (line.containerType().startsWith("RE") || line.containerType().startsWith("4")) ? 2 : 1;
        }

        events.publish(VesselDomainEvents.DISCHARGE_PLAN_CREATED,
                new VesselDomainEvents.DischargePlanCreatedPayload(
                        plan.getId(), v.getId(), voyageNumber, lines.size(), teus, plan.getCreatedAt()));
        return plan;
    }

    @Transactional(readOnly = true)
    public DischargePlan getPlan(UUID id) {
        return plans.findById(id)
                .orElseThrow(() -> new ApiException(404, "plan_not_found", "Discharge plan not found"));
    }

    @Transactional(readOnly = true)
    public List<DischargeContainer> planContainers(UUID planId) {
        return containers.findByDischargePlanIdOrderByVesselPosition(planId);
    }

    @Transactional(readOnly = true)
    public List<DischargePlan> byVessel(UUID vesselId) {
        vessels.get(vesselId);
        return plans.findByVesselIdOrderByCreatedAtDesc(vesselId);
    }

    @Transactional
    public DischargeContainer updateProgress(UUID planId, String containerNumber,
                                             DischargeContainerStatus status,
                                             String actualYardPosition, Instant dischargedAt,
                                             Instant stackedAt, String equipmentId,
                                             String operator, String notes) {
        DischargePlan plan = getPlan(planId);
        if (plan.getStatus() == DischargePlanStatus.COMPLETED) {
            throw new ApiException(409, "plan_completed", "Discharge plan is already completed");
        }
        List<DischargeContainer> matches = containers.findByDischargePlanIdOrderByVesselPosition(planId)
                .stream().filter(c -> c.getContainerNumber().equals(containerNumber)).toList();
        if (matches.isEmpty()) {
            throw new ApiException(404, "container_not_on_plan",
                    "Container " + containerNumber + " is not on discharge plan " + planId);
        }
        if (matches.size() > 1) {
            throw new ApiException(409, "duplicate_container",
                    "Container " + containerNumber + " appears more than once on the plan");
        }
        DischargeContainer c = matches.get(0);
        DischargeContainerStatus from = c.getStatus();
        if (status == from) {
            return c;
        }

        switch (status) {
            case DISCHARGED -> {
                requireFrom(from, DischargeContainerStatus.PLANNED,
                        "discharged", c);
                c.setStatus(DischargeContainerStatus.DISCHARGED);
                c.setDischargedAt(dischargedAt != null ? dischargedAt : clock.now());
                c.setEquipmentId(equipmentId);
                c.setOperator(operator);
                c.setNotes(notes == null ? c.getNotes() : notes);
                if (properties.isVerifyContainersAtPlanning()) {
                    containerClient.changeStatus(containerId(c), "DISCHARGED",
                            plan.getVesselId().toString(), "Discharged from vessel");
                }
            }
            case IN_TRANSIT -> {
                requireFrom(from, DischargeContainerStatus.DISCHARGED, "in_transit", c);
                c.setStatus(DischargeContainerStatus.IN_TRANSIT);
            }
            case STACKED -> {
                requireFrom(from, DischargeContainerStatus.IN_TRANSIT, "stacked", c);
                c.setStatus(DischargeContainerStatus.STACKED);
                c.setActualYardPosition(actualYardPosition);
                c.setStackedAt(stackedAt != null ? stackedAt : clock.now());
                if (actualYardPosition != null && !actualYardPosition.isBlank()) {
                    placeInYard(c);
                }
            }
            case MISROUTED -> {
                requireFrom(from, DischargeContainerStatus.IN_TRANSIT, "misrouted", c);
                c.setStatus(DischargeContainerStatus.MISROUTED);
                c.setNotes(notes == null ? c.getNotes() : (c.getNotes() == null ? notes : c.getNotes() + " | " + notes));
            }
            default -> throw new ApiException(422, "invalid_status", "Unsupported status " + status);
        }
        DischargeContainer saved = containers.save(c);
        publishProgressIfNeeded(plan);
        return saved;
    }

    void publishProgressIfNeeded(DischargePlan plan) {
        if (plan.getStatus() == DischargePlanStatus.COMPLETED) {
            return;
        }
        Map<DischargeContainerStatus, Long> counts = containers
                .findByDischargePlanIdOrderByVesselPosition(plan.getId()).stream()
                .collect(Collectors.groupingBy(DischargeContainer::getStatus, Collectors.counting()));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) {
            return;
        }
        long fined =
                counts.getOrDefault(DischargeContainerStatus.DISCHARGED, 0L)
                + counts.getOrDefault(DischargeContainerStatus.IN_TRANSIT, 0L)
                + counts.getOrDefault(DischargeContainerStatus.STACKED, 0L);
        if (fined == total) {
            plan.setStatus(DischargePlanStatus.COMPLETED);
            plans.save(plan);
        }
        events.publish(VesselDomainEvents.DISCHARGE_PROGRESS_UPDATED,
                new VesselDomainEvents.DischargeProgressUpdatedPayload(
                        plan.getId(), plan.getVesselId(), (int) total,
                        counts.getOrDefault(DischargeContainerStatus.DISCHARGED, 0L).intValue(),
                        counts.getOrDefault(DischargeContainerStatus.IN_TRANSIT, 0L).intValue(),
                        counts.getOrDefault(DischargeContainerStatus.STACKED, 0L).intValue(),
                        counts.getOrDefault(DischargeContainerStatus.PLANNED, 0L).intValue()));
    }

    private UUID containerId(DischargeContainer c) {
        return c.getId();
    }

    private void requireFrom(DischargeContainerStatus from, DischargeContainerStatus expected,
                             String action, DischargeContainer c) {
        if (from != expected) {
            throw new ApiException(409, "invalid_transition",
                    "Container " + c.getContainerNumber() + " cannot be marked " + action
                            + " from " + from);
        }
    }

    private void placeInYard(DischargeContainer c) {
        String[] pos = c.getActualYardPosition().split("-", -1);
        if (pos.length != 4) {
            throw new ApiException(422, "invalid_yard_position",
                    "Yard position must be block-bay-row-tier, got " + c.getActualYardPosition());
        }
        yardClient.placeContainer(pos[0], pos[1], pos[2], pos[3],
                c.getContainerNumber(), c.getId());
    }

    public record ContainerLine(String containerNumber, String containerType, Double weightKg,
                                String vesselPosition, String yardTarget, String priority,
                                boolean dangerousGoods, boolean reefer) {
    }
}