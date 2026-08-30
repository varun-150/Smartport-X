package com.smartportx.container.util;

import com.smartportx.container.domain.ContainerStatus;

import java.util.Map;
import java.util.Set;

public final class ContainerStateMachine {

    private static final Map<ContainerStatus, Set<ContainerStatus>> TRANSITIONS = Map.of(
            ContainerStatus.REGISTERED, Set.of(ContainerStatus.AT_GATE, ContainerStatus.HELD),
            ContainerStatus.AT_GATE, Set.of(ContainerStatus.IN_YARD, ContainerStatus.HELD),
            ContainerStatus.IN_YARD, Set.of(ContainerStatus.LOADED, ContainerStatus.HELD),
            ContainerStatus.LOADED, Set.of(ContainerStatus.DISCHARGED),
            ContainerStatus.DISCHARGED, Set.of(ContainerStatus.AT_GATE_OUT, ContainerStatus.HELD),
            ContainerStatus.AT_GATE_OUT, Set.of(ContainerStatus.DEPARTED, ContainerStatus.HELD),
            ContainerStatus.HELD, Set.of(ContainerStatus.AT_GATE, ContainerStatus.IN_YARD,
                    ContainerStatus.DISCHARGED, ContainerStatus.AT_GATE_OUT),
            ContainerStatus.DEPARTED, Set.of());

    private ContainerStateMachine() {
    }

    public static boolean canTransition(ContainerStatus from, ContainerStatus to) {
        Set<ContainerStatus> targets = TRANSITIONS.get(from);
        return targets != null && targets.contains(to);
    }

    public static Set<ContainerStatus> legalTargets(ContainerStatus from) {
        return TRANSITIONS.getOrDefault(from, Set.of());
    }
}