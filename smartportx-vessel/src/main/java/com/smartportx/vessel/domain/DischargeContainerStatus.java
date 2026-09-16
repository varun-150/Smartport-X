package com.smartportx.vessel.domain;

import java.util.Set;

public enum DischargeContainerStatus {
    PLANNED,
    DISCHARGED,
    IN_TRANSIT,
    STACKED,
    MISROUTED;

    public static final Set<DischargeContainerStatus> OFF_VESSEL =
            Set.of(DISCHARGED, IN_TRANSIT, STACKED);

    public boolean isOffVessel() {
        return OFF_VESSEL.contains(this);
    }
}