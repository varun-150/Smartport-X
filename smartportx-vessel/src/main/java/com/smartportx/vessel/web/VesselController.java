package com.smartportx.vessel.web;

import com.smartportx.common.error.ApiException;
import com.smartportx.vessel.domain.DischargeContainer;
import com.smartportx.vessel.domain.DischargeContainerStatus;
import com.smartportx.vessel.domain.DischargePlan;
import com.smartportx.vessel.domain.DischargePlanStatus;
import com.smartportx.vessel.domain.Vessel;
import com.smartportx.vessel.domain.VesselSchedule;
import com.smartportx.vessel.service.DischargeService;
import com.smartportx.vessel.service.VesselScheduleService;
import com.smartportx.vessel.service.VesselService;
import com.smartportx.vessel.support.VesselIds;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class VesselController {

    private final VesselService vessels;
    private final VesselScheduleService schedules;
    private final DischargeService discharges;
    private final double movesPerHour;

    public VesselController(VesselService vessels, VesselScheduleService schedules,
                            DischargeService discharges,
                            @Value("${smartportx.vessel.moves-per-hour:25}") double movesPerHour) {
        this.vessels = vessels;
        this.schedules = schedules;
        this.discharges = discharges;
        this.movesPerHour = movesPerHour;
    }

    // ---- vessel registry ----

    @PostMapping("/vessels")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TERMINAL_OP')")
    public VesselDtos.VesselResponse registerVessel(@Valid @RequestBody VesselDtos.RegisterVesselRequest req) {
        return toResponse(vessels.register(req.imoNumber(), req.vesselName(), req.vesselType(),
                req.lengthM(), req.beamM(), req.draftM(), req.teuCapacity(), req.grossTonnage(),
                req.flag(), req.operator(), req.callSign()));
    }

    @GetMapping("/vessels")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('vessel:read')")
    public VesselDtos.ListResponse<VesselDtos.VesselResponse> listVessels() {
        List<VesselDtos.VesselResponse> items = vessels.list().stream()
                .map(this::toResponse).collect(Collectors.toList());
        return new VesselDtos.ListResponse<>(items, null);
    }

    @GetMapping("/vessels/{vesselId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('vessel:read')")
    public VesselDtos.VesselDetailResponse getVessel(@PathVariable String vesselId) {
        UUID id = VesselIds.toUuid(vesselId, VesselIds.VESSEL_PREFIX);
        return toDetail(vessels.get(id));
    }

    @PutMapping("/vessels/{vesselId}")
    @PreAuthorize("hasAnyRole('ADMIN','TERMINAL_OP')")
    public VesselDtos.VesselResponse updateVessel(@PathVariable String vesselId,
                                                  @Valid @RequestBody VesselDtos.RegisterVesselRequest req) {
        UUID id = VesselIds.toUuid(vesselId, VesselIds.VESSEL_PREFIX);
        return toResponse(vessels.update(id, req.imoNumber(), req.vesselName(), req.vesselType(),
                req.lengthM(), req.beamM(), req.draftM(), req.teuCapacity(), req.grossTonnage(),
                req.flag(), req.operator(), req.callSign()));
    }

    @DeleteMapping("/vessels/{vesselId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteVessel(@PathVariable String vesselId) {
        vessels.delete(VesselIds.toUuid(vesselId, VesselIds.VESSEL_PREFIX));
    }

    // ---- schedules ----

    @PostMapping("/vessels/{vesselId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TERMINAL_OP','VESSEL_PLANNER')")
    public VesselDtos.ScheduleResponse addSchedule(@PathVariable String vesselId,
                                                   @Valid @RequestBody VesselDtos.AddScheduleRequest req) {
        UUID id = VesselIds.toUuid(vesselId, VesselIds.VESSEL_PREFIX);
        return toSchedule(schedules.add(id, req.voyageNumber(), req.berthNumber(), req.eta(), req.etd(),
                req.priority(), req.portOfCall(), req.nextPort(), req.estimatedTeUs()));
    }

    @GetMapping("/vessels/{vesselId}/schedules")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('vessel:read')")
    public VesselDtos.ListResponse<VesselDtos.ScheduleResponse> listSchedules(@PathVariable String vesselId) {
        UUID id = VesselIds.toUuid(vesselId, VesselIds.VESSEL_PREFIX);
        List<VesselDtos.ScheduleResponse> items = schedules.byVessel(id).stream()
                .map(this::toSchedule).collect(Collectors.toList());
        return new VesselDtos.ListResponse<>(items, null);
    }

    @PatchMapping("/schedules/{scheduleId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','TERMINAL_OP')")
    public VesselDtos.ScheduleResponse updateScheduleStatus(
            @PathVariable String scheduleId,
            @Valid @RequestBody VesselDtos.UpdateScheduleStatusRequest req) {
        UUID id = VesselIds.toUuid(scheduleId, VesselIds.SCHEDULE_PREFIX);
        return toSchedule(schedules.updateStatus(id, req.status()));
    }

    @GetMapping("/schedules/in-port")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('vessel:read')")
    public VesselDtos.ListResponse<VesselDtos.ScheduleResponse> inPortSchedules() {
        List<VesselDtos.ScheduleResponse> items = schedules.inPort().stream()
                .map(this::toSchedule).collect(Collectors.toList());
        return new VesselDtos.ListResponse<>(items, null);
    }

    // ---- discharge plans ----

    @PostMapping("/vessels/{vesselId}/discharge-plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','VESSEL_PLANNER','TERMINAL_OP')")
    public DischargeDtos.PlanResponse createDischargePlan(
            @PathVariable String vesselId,
            @Valid @RequestBody DischargeDtos.CreatePlanRequest req) {
        UUID id = VesselIds.toUuid(vesselId, VesselIds.VESSEL_PREFIX);
        List<DischargeService.ContainerLine> lines = req.containers().stream()
                .map(c -> new DischargeService.ContainerLine(
                        c.containerNumber(), c.containerType(), c.weightKg(),
                        c.vesselPosition(), c.yardTarget(),
                        c.priority() == null ? DischargeService.PRIORITY_NORMAL : c.priority(),
                        Boolean.TRUE.equals(c.dangerousGoods()), Boolean.TRUE.equals(c.reefer())))
                .collect(Collectors.toList());
        DischargePlan plan = discharges.create(id, req.voyageNumber(), lines);
        return toPlan(plan);
    }

    @GetMapping("/vessels/{vesselId}/discharge-plans")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('vessel:read')")
    public DischargeDtos.ListResponse<DischargeDtos.PlanResponse> listDischargePlans(@PathVariable String vesselId) {
        UUID id = VesselIds.toUuid(vesselId, VesselIds.VESSEL_PREFIX);
        List<DischargeDtos.PlanResponse> items = discharges.byVessel(id).stream()
                .map(this::toPlan).collect(Collectors.toList());
        return new DischargeDtos.ListResponse<>(items, null);
    }

    @GetMapping("/discharge-plans/{planId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('vessel:read')")
    public DischargeDtos.PlanResponse getDischargePlan(@PathVariable String planId) {
        UUID id = VesselIds.toUuid(planId, VesselIds.PLAN_PREFIX);
        return toPlan(discharges.getPlan(id));
    }

    @PatchMapping("/discharge-plans/{planId}/progress")
    @PreAuthorize("hasAnyRole('ADMIN','TERMINAL_OP')")
    public DischargeDtos.PlanResponse updateDischargeProgress(
            @PathVariable String planId,
            @Valid @RequestBody DischargeDtos.ProgressUpdateRequest req) {
        UUID id = VesselIds.toUuid(planId, VesselIds.PLAN_PREFIX);
        discharges.updateProgress(id, req.containerNumber(),
                DischargeService.parseContainerStatus(req.status()),
                req.actualYardPosition(), req.dischargedAt(), req.stackedAt(),
                req.equipmentId(), req.operator(), req.notes());
        return toPlan(discharges.getPlan(id));
    }

    // ---- mappers ----

    private VesselDtos.VesselResponse toResponse(Vessel v) {
        return new VesselDtos.VesselResponse(
                VesselIds.vessel(v.getId()), v.getImoNumber(), v.getVesselName(), v.getVesselType(),
                v.getLengthM(), v.getBeamM(), v.getDraftM(), v.getTeuCapacity(),
                v.getGrossTonnage(), v.getCallSign(), v.getFlag(), v.getOperator(),
                v.getCreatedAt(), v.getUpdatedAt());
    }

    private VesselDtos.VesselDetailResponse toDetail(Vessel v) {
        String currentStatus = schedules.active().stream()
                .filter(s -> s.getVesselId().equals(v.getId()))
                .findFirst().map(s -> s.getStatus().name()).orElse(null);
        return new VesselDtos.VesselDetailResponse(
                VesselIds.vessel(v.getId()), v.getImoNumber(), v.getVesselName(), v.getVesselType(),
                v.getLengthM(), v.getBeamM(), v.getDraftM(), v.getTeuCapacity(),
                v.getGrossTonnage(), v.getCallSign(), v.getFlag(), v.getOperator(),
                currentStatus, v.getCreatedAt(), v.getUpdatedAt());
    }

    private VesselDtos.ScheduleResponse toSchedule(VesselSchedule s) {
        return new VesselDtos.ScheduleResponse(
                VesselIds.schedule(s.getId()), VesselIds.vessel(s.getVesselId()), s.getVoyageNumber(),
                s.getBerthNumber(), s.getPriority(), s.getStatus(), s.getEta(), s.getEtd(),
                s.getPortOfCall(), s.getNextPort(), s.getEstimatedTeUs(), s.getActualTeUs(),
                s.getArrivedAt(), s.getDepartedAt(), s.getCreatedAt(), s.getUpdatedAt());
    }

    private DischargeDtos.PlanResponse toPlan(DischargePlan plan) {
        List<DischargeContainer> list = discharges.planContainers(plan.getId());
        long total = list.size();
        int teus = list.stream().mapToInt(c ->
                (c.getContainerType().startsWith("RE") || c.getContainerType().startsWith("4")) ? 2 : 1).sum();
        long discharged = list.stream().filter(c -> c.getStatus().isOffVessel()).count();
        long pending = total - discharged;

        DischargeDtos.PlanProgress progress = new DischargeDtos.PlanProgress(
                (int) total,
                (int) count(list, c -> c.getStatus() == DischargeContainerStatus.DISCHARGED),
                (int) count(list, c -> c.getStatus() == DischargeContainerStatus.IN_TRANSIT),
                (int) count(list, c -> c.getStatus() == DischargeContainerStatus.STACKED),
                (int) pending,
                total == 0 ? 0.0 : (100.0 * discharged) / total,
                estimateCompletion(discharged, total, plan));

        List<DischargeDtos.DischargeContainerResponse> containers = list.stream()
                .map(c -> new DischargeDtos.DischargeContainerResponse(
                        c.getContainerNumber(), c.getContainerType(), c.getWeightKg(),
                        c.getVesselPosition(), c.getYardTarget(), c.getStatus().name(),
                        c.getActualYardPosition(), c.getDischargedAt(), c.getStackedAt(), c.getOperator()))
                .collect(Collectors.toList());

        return new DischargeDtos.PlanResponse(
                VesselIds.plan(plan.getId()), VesselIds.vessel(plan.getVesselId()),
                plan.getVoyageNumber(), plan.getStatus().name(), (int) total, teus,
                (int) Math.round(teus / movesPerHour * 60), plan.getCreatedAt(), progress, containers);
    }

    private java.time.Instant estimateCompletion(long completed, long total, DischargePlan plan) {
        if (plan.getStatus() == DischargePlanStatus.COMPLETED) {
            return null;
        }
        if (movesPerHour <= 0) {
            return null;
        }
        long remaining = total - completed;
        long minutes = Math.round(remaining / movesPerHour * 60);
        return plan.getCreatedAt().plus(java.time.Duration.ofMinutes(minutes));
    }

    private long count(List<DischargeContainer> list, java.util.function.Predicate<DischargeContainer> pred) {
        return list.stream().filter(pred).count();
    }
}