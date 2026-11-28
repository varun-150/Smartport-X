package com.smartportx.identity.security;

import com.smartportx.identity.domain.RoleName;

import java.util.List;

public final class RolePermissions {

    private static final List<String> ADMIN = List.of(
            "users.read", "users.write", "users.disable",
            "vessels.read", "vessels.write",
            "yards.read", "yards.write",
            "gates.read", "gates.write",
            "reports.read");

    private static final List<String> TERMINAL_OP = List.of(
            "vessels.read", "vessels.write",
            "cargo.read", "cargo.write");

    private static final List<String> YARD_MANAGER = List.of(
            "yards.read", "yards.write",
            "gates.read", "vessels.read");

    private static final List<String> GATE_OPERATOR = List.of(
            "gates.read", "gates.write",
            "vessels.read");

    private static final List<String> READONLY = List.of(
            "reports.read", "gates.read", "yards.read", "vessels.read");

    private RolePermissions() {
    }

    public static List<String> of(RoleName role) {
        if (role == null) {
            return READONLY;
        }
        return switch (role) {
            case ADMIN -> ADMIN;
            case TERMINAL_OP -> TERMINAL_OP;
            case YARD_MANAGER -> YARD_MANAGER;
            case GATE_OPERATOR -> GATE_OPERATOR;
            case READONLY -> READONLY;
        };
    }
}