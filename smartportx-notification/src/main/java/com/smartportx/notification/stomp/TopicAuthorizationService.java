package com.smartportx.notification.stomp;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authorizes a STOMP SUBSCRIBE destination against the caller's granted authorities.
 *
 * <p>All destinations are {@code /topic/<namespace>/...}. Rules are matched by exact
 * path segments; a rule ending in {@code *} matches a single non-empty segment.
 * {@code ROLE_ADMIN} is always allowed. A destination that matches no rule is denied
 * (fail-closed). API-CONTRACTS &sect;4.3 / DATA-ARCHITECTURE &sect;11.2.</p>
 */
@Service
public class TopicAuthorizationService {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_TERMINAL_OP = "ROLE_TERMINAL_OP";
    public static final String ROLE_YARD_MANAGER = "ROLE_YARD_MANAGER";
    public static final String ROLE_GATE_OPERATOR = "ROLE_GATE_OPERATOR";
    public static final String ANY_AUTHENTICATED = "ANY_AUTHENTICATED";

    private static final List<Rule> RULES = List.of(
            new Rule("/topic/yard-status", ROLE_TERMINAL_OP),
            new Rule("/topic/yard-zone/*", ROLE_YARD_MANAGER),
            new Rule("/topic/gate-status", ROLE_GATE_OPERATOR),
            new Rule("/topic/gate-lane/*", ROLE_GATE_OPERATOR),
            new Rule("/topic/vessel-status", ROLE_TERMINAL_OP),
            new Rule("/topic/vessel/*", ROLE_TERMINAL_OP),
            new Rule("/topic/containers/*", ANY_AUTHENTICATED),
            new Rule("/topic/appointments/*", ROLE_GATE_OPERATOR),
            new Rule("/topic/customs/*", ROLE_TERMINAL_OP),
            new Rule("/topic/dashboard/summary", ANY_AUTHENTICATED),
            new Rule("/topic/alerts", ANY_AUTHENTICATED),
            new Rule("/topic/alerts/*", ANY_AUTHENTICATED)
    );

    private record Rule(String[] segments, String requiredAuthority) {
        Rule(String destination, String requiredAuthority) {
            this(split(destination), requiredAuthority);
        }
    }

    /**
     * @param destination the SUBSCRIBE destination, e.g. {@code /topic/yard-zone/B1}
     * @param authorities granted authorities of the authenticated user
     * @return {@code true} when allowed
     */
    public boolean isAllowed(String destination, Collection<? extends org.springframework.security.core.GrantedAuthority> authorities) {
        if (destination == null || destination.isBlank()) {
            return false;
        }
        if (authorities == null || authorities.isEmpty()) {
            return false;
        }
        Set<String> granted = authorities.stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        if (granted.contains(ROLE_ADMIN)) {
            return true;
        }
        String[] destinationSegments = split(destination);
        for (Rule rule : RULES) {
            if (matches(rule.segments(), destinationSegments)) {
                return rule.requiredAuthority().equals(ANY_AUTHENTICATED)
                        || granted.contains(rule.requiredAuthority());
            }
        }
        return false;
    }

    private static boolean matches(String[] pattern, String[] destination) {
        if (pattern.length != destination.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (!pattern[i].equals("*") && !pattern[i].equals(destination[i])) {
                return false;
            }
        }
        return true;
    }

    private static String[] split(String destination) {
        return Arrays.stream(destination.split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
    }
}