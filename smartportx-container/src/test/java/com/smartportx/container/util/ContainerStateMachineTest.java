package com.smartportx.container.util;

import com.smartportx.container.domain.ContainerStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerStateMachineTest {

    @Test
    void allLegalEdgesAllowed() {
        assertTrue(ContainerStateMachine.canTransition(ContainerStatus.REGISTERED, ContainerStatus.AT_GATE));
        assertTrue(ContainerStateMachine.canTransition(ContainerStatus.REGISTERED, ContainerStatus.HELD));
        assertTrue(ContainerStateMachine.canTransition(ContainerStatus.AT_GATE, ContainerStatus.IN_YARD));
        assertTrue(ContainerStateMachine.canTransition(ContainerStatus.AT_GATE, ContainerStatus.HELD));
        assertTrue(ContainerStateMachine.canTransition(ContainerStatus.IN_YARD, ContainerStatus.LOADED));
        assertTrue(ContainerStateMachine.canTransition(ContainerStatus.IN_YARD, ContainerStatus.HELD));
        assertTrue(ContainerStateMachine.canTransition(ContainerStatus.LOADED, ContainerStatus.DISCHARGED));
        assertTrue(ContainerStateMachine.canTransition(ContainerStatus.DISCHARGED, ContainerStatus.AT_GATE_OUT));
        assertTrue(ContainerStateMachine.canTransition(ContainerStatus.DISCHARGED, ContainerStatus.HELD));
        assertTrue(ContainerStateMachine.canTransition(ContainerStatus.AT_GATE_OUT, ContainerStatus.DEPARTED));
        assertTrue(ContainerStateMachine.canTransition(ContainerStatus.AT_GATE_OUT, ContainerStatus.HELD));
        assertTrue(ContainerStateMachine.canTransition(ContainerStatus.HELD, ContainerStatus.AT_GATE));
        assertTrue(ContainerStateMachine.canTransition(ContainerStatus.HELD, ContainerStatus.IN_YARD));
        assertTrue(ContainerStateMachine.canTransition(ContainerStatus.HELD, ContainerStatus.DISCHARGED));
        assertTrue(ContainerStateMachine.canTransition(ContainerStatus.HELD, ContainerStatus.AT_GATE_OUT));
    }

    @Test
    void everyIllegalPairRejected() {
        EnumSet<ContainerStatus> all = EnumSet.allOf(ContainerStatus.class);
        for (ContainerStatus from : all) {
            Set<ContainerStatus> legal = ContainerStateMachine.legalTargets(from);
            for (ContainerStatus to : all) {
                if (!legal.contains(to)) {
                    assertFalse(ContainerStateMachine.canTransition(from, to),
                            from + " -> " + to + " must be rejected");
                }
            }
        }
    }

    @Test
    void sameStatusAlwaysRejected() {
        for (ContainerStatus status : ContainerStatus.values()) {
            assertFalse(ContainerStateMachine.canTransition(status, status));
        }
    }

    @Test
    void departedIsTerminal() {
        assertTrue(ContainerStateMachine.legalTargets(ContainerStatus.DEPARTED).isEmpty());
        for (ContainerStatus status : ContainerStatus.values()) {
            assertFalse(ContainerStateMachine.canTransition(ContainerStatus.DEPARTED, status));
        }
    }
}