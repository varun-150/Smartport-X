package com.smartportx.notification.stomp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TopicAuthorizationServiceTest {

    private TopicAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new TopicAuthorizationService();
    }

    // --- ADMIN always passes ---

    @Test
    void adminIsAllowedEverything() {
        List<GrantedAuthority> admin = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        assertThat(service.isAllowed("/topic/yard-status", admin)).isTrue();
        assertThat(service.isAllowed("/topic/yard-zone/B1", admin)).isTrue();
        assertThat(service.isAllowed("/topic/gate-status", admin)).isTrue();
        assertThat(service.isAllowed("/topic/gate-lane/L1", admin)).isTrue();
        assertThat(service.isAllowed("/topic/vessel-status", admin)).isTrue();
        assertThat(service.isAllowed("/topic/vessel/V1", admin)).isTrue();
        assertThat(service.isAllowed("/topic/containers/MSKU123", admin)).isTrue();
        assertThat(service.isAllowed("/topic/appointments/A1", admin)).isTrue();
        assertThat(service.isAllowed("/topic/customs/C1", admin)).isTrue();
        assertThat(service.isAllowed("/topic/dashboard/summary", admin)).isTrue();
        assertThat(service.isAllowed("/topic/alerts", admin)).isTrue();
        assertThat(service.isAllowed("/topic/alerts/warning", admin)).isTrue();
    }

    // --- role-based access ---

    @Test
    void terminalOp_canAccessYardStatusAndVesselAndCustoms() {
        List<GrantedAuthority> auth = List.of(new SimpleGrantedAuthority("ROLE_TERMINAL_OP"));
        assertThat(service.isAllowed("/topic/yard-status", auth)).isTrue();
        assertThat(service.isAllowed("/topic/vessel-status", auth)).isTrue();
        assertThat(service.isAllowed("/topic/vessel/V1", auth)).isTrue();
        assertThat(service.isAllowed("/topic/customs/C1", auth)).isTrue();
    }

    @Test
    void terminalOp_cannotAccessGateLaneOrYardZone() {
        List<GrantedAuthority> auth = List.of(new SimpleGrantedAuthority("ROLE_TERMINAL_OP"));
        assertThat(service.isAllowed("/topic/gate-status", auth)).isFalse();
        assertThat(service.isAllowed("/topic/gate-lane/L1", auth)).isFalse();
        assertThat(service.isAllowed("/topic/yard-zone/B1", auth)).isFalse();
        assertThat(service.isAllowed("/topic/appointments/A1", auth)).isFalse();
    }

    @Test
    void yardManager_canAccessYardZone() {
        List<GrantedAuthority> auth = List.of(new SimpleGrantedAuthority("ROLE_YARD_MANAGER"));
        assertThat(service.isAllowed("/topic/yard-zone/B1", auth)).isTrue();
    }

    @Test
    void yardManager_cannotAccessGateStatus() {
        List<GrantedAuthority> auth = List.of(new SimpleGrantedAuthority("ROLE_YARD_MANAGER"));
        assertThat(service.isAllowed("/topic/gate-status", auth)).isFalse();
    }

    @Test
    void gateOperator_canAccessGateTopicsAndAppointments() {
        List<GrantedAuthority> auth = List.of(new SimpleGrantedAuthority("ROLE_GATE_OPERATOR"));
        assertThat(service.isAllowed("/topic/gate-status", auth)).isTrue();
        assertThat(service.isAllowed("/topic/gate-lane/L1", auth)).isTrue();
        assertThat(service.isAllowed("/topic/appointments/A1", auth)).isTrue();
    }

    @Test
    void gateOperator_cannotAccessVesselOrYard() {
        List<GrantedAuthority> auth = List.of(new SimpleGrantedAuthority("ROLE_GATE_OPERATOR"));
        assertThat(service.isAllowed("/topic/vessel-status", auth)).isFalse();
        assertThat(service.isAllowed("/topic/vessel/V1", auth)).isFalse();
        assertThat(service.isAllowed("/topic/yard-zone/B1", auth)).isFalse();
    }

    // --- ANY_AUTHENTICATED ---

    @Test
    void anyAuthenticated_canAccessContainerAndDashboardAndAlerts() {
        List<GrantedAuthority> auth = List.of(new SimpleGrantedAuthority("ROLE_TERMINAL_OP"));
        assertThat(service.isAllowed("/topic/containers/MSKU123", auth)).isTrue();
        assertThat(service.isAllowed("/topic/dashboard/summary", auth)).isTrue();
        assertThat(service.isAllowed("/topic/alerts", auth)).isTrue();
        assertThat(service.isAllowed("/topic/alerts/warning", auth)).isTrue();
    }

    @Test
    void readonly_canAccessContainerAndDashboardAndAlerts() {
        List<GrantedAuthority> auth = List.of(new SimpleGrantedAuthority("ROLE_READONLY"));
        assertThat(service.isAllowed("/topic/containers/MSKU123", auth)).isTrue();
        assertThat(service.isAllowed("/topic/dashboard/summary", auth)).isTrue();
        assertThat(service.isAllowed("/topic/alerts", auth)).isTrue();
    }

    @Test
    void readonly_cannotAccessYardStatus() {
        List<GrantedAuthority> auth = List.of(new SimpleGrantedAuthority("ROLE_READONLY"));
        assertThat(service.isAllowed("/topic/yard-status", auth)).isFalse();
    }

    // --- unknown / no match -> denied ---

    @Test
    void unmatchedDestination_isDenied() {
        List<GrantedAuthority> auth = List.of(new SimpleGrantedAuthority("ROLE_TERMINAL_OP"));
        assertThat(service.isAllowed("/topic/unknown", auth)).isFalse();
        assertThat(service.isAllowed("/topic/containers", auth)).isFalse(); // wrong segment count
        assertThat(service.isAllowed("/topic/containers/MSKU/deep", auth)).isFalse(); // extra segment depth
    }

    // --- edge cases ---

    @Test
    void nullDestination_isDenied() {
        assertThat(service.isAllowed(null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))).isFalse();
    }

    @Test
    void blankDestination_isDenied() {
        assertThat(service.isAllowed("  ", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))).isFalse();
    }

    @Test
    void nullAuthorities_isDenied() {
        assertThat(service.isAllowed("/topic/yard-status", null)).isFalse();
    }

    @Test
    void emptyAuthorities_isDenied() {
        assertThat(service.isAllowed("/topic/yard-status", List.of())).isFalse();
    }

    @Test
    void plainUserWithNoRoles_cannotAccessAnything() {
        // ANY_AUTHENTICATED requires at least one authority
        assertThat(service.isAllowed("/topic/containers/MSKU123", List.of())).isFalse();
    }

    // --- wildcard segment matching ---

    @Test
    void wildcardSegment_matchesExactlyOneSegment() {
        List<GrantedAuthority> gateAuth = List.of(new SimpleGrantedAuthority("ROLE_GATE_OPERATOR"));
        assertThat(service.isAllowed("/topic/gate-lane/LANE1", gateAuth)).isTrue();
        assertThat(service.isAllowed("/topic/gate-lane/", gateAuth)).isFalse();  // trailing slash = empty segment
    }
}