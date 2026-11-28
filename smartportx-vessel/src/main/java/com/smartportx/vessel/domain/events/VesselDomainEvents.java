package com.smartportx.vessel.domain.events;

import java.time.Instant;
import java.util.UUID;

public final class VesselDomainEvents {

    public static final String VESSEL_ARRIVED = "vessel.VesselArrived";
    public static final String VESSEL_DEPARTED = "vessel.VesselDeparted";
    public static final String DISCHARGE_PLAN_CREATED = "vessel.DischargePlanCreated";
    public static final String DISCHARGE_PROGRESS_UPDATED = "vessel.DischargeProgressUpdated";

    private VesselDomainEvents() {
    }

    public record VesselArrivedPayload(UUID vesselId, String vesselName, String imoNumber,
                                       String voyageNumber, String berthNumber, Instant arrivedAt) {
    }

    public record VesselDepartedPayload(UUID vesselId, String vesselName, String imoNumber,
                                        String voyageNumber, Instant departedAt) {
    }

    public record DischargePlanCreatedPayload(UUID planId, UUID vesselId, String voyageNumber,
                                              int totalContainers, int totalTeUs, Instant createdAt) {
    }

    public record DischargeProgressUpdatedPayload(UUID planId, UUID vesselId,
                                                  int totalContainers, int dischargedCount,
                                                  int inTransitCount, int stackedCount,
                                                  int pendingCount) {
    }
}