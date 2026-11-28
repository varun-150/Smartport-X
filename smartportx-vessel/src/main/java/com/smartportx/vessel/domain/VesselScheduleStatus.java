package com.smartportx.vessel.domain;

import java.util.Set;

public enum VesselScheduleStatus {
    SCHEDULED,
    IN_TRANSIT,
    ARRIVED,
    WORKING,
    DEPARTED,
    CANCELLED;

    public static final Set<VesselScheduleStatus> ACTIVE =
            Set.of(IN_TRANSIT, ARRIVED, WORKING);
    public static final Set<VesselScheduleStatus> IN_PORT =
            Set.of(ARRIVED, WORKING);
}