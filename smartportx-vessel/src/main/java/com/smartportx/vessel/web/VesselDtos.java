package com.smartportx.vessel.web;

import com.smartportx.vessel.domain.SchedulePriority;
import com.smartportx.vessel.domain.VesselScheduleStatus;
import com.smartportx.vessel.domain.VesselType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class VesselDtos {

    private VesselDtos() {
    }

    // ---- requests ----

    public record RegisterVesselRequest(
            @NotBlank @Pattern(regexp = "IMO[0-9]{7}", message = "Must match IMO[0-9]{7}")
            String imoNumber,
            @NotBlank String vesselName,
            @NotNull VesselType vesselType,
            @Positive double lengthM,
            @Positive double beamM,
            @Positive double draftM,
            Integer teuCapacity,
            Double grossTonnage,
            String flag,
            String operator,
            String callSign) {
    }

    public record AddScheduleRequest(
            @NotBlank String voyageNumber,
            @NotBlank String berthNumber,
            @NotNull Instant eta,
            @NotNull Instant etd,
            SchedulePriority priority,
            String portOfCall,
            String nextPort,
            Integer estimatedTeUs) {
    }

    public record UpdateScheduleStatusRequest(@NotNull VesselScheduleStatus status) {
    }

    // ---- responses ----

    public record VesselResponse(
            String id,
            String imoNumber,
            String vesselName,
            VesselType vesselType,
            double lengthM,
            double beamM,
            double draftM,
            Integer teuCapacity,
            Double grossTonnage,
            String callSign,
            String flag,
            String operator,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record VesselDetailResponse(
            String id,
            String imoNumber,
            String vesselName,
            VesselType vesselType,
            double lengthM,
            double beamM,
            double draftM,
            Integer teuCapacity,
            Double grossTonnage,
            String callSign,
            String flag,
            String operator,
            String currentStatus,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ScheduleResponse(
            String id,
            String vesselId,
            String voyageNumber,
            String berthNumber,
            SchedulePriority priority,
            VesselScheduleStatus status,
            Instant eta,
            Instant etd,
            String portOfCall,
            String nextPort,
            Integer estimatedTeUs,
            Integer actualTeUs,
            Instant arrivedAt,
            Instant departedAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ListResponse<T>(List<T> items, String nextCursor) {
    }
}