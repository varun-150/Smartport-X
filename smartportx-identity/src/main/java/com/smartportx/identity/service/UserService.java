package com.smartportx.identity.service;

import com.smartportx.common.error.ConflictException;
import com.smartportx.common.error.NotFoundException;
import com.smartportx.identity.domain.PasswordHistory;
import com.smartportx.identity.domain.Role;
import com.smartportx.identity.domain.User;
import com.smartportx.identity.domain.UserRole;
import com.smartportx.identity.repository.PasswordHistoryRepository;
import com.smartportx.identity.repository.RoleRepository;
import com.smartportx.identity.repository.UserRepository;
import com.smartportx.identity.repository.UserRoleRepository;
import com.smartportx.identity.security.AuditLog;
import com.smartportx.identity.web.dto.CreateUserRequest;
import com.smartportx.identity.web.dto.Status;
import com.smartportx.identity.web.dto.UpdateUserRequest;
import com.smartportx.identity.web.dto.UserResponse;
import com.smartportx.identity.web.dto.UserResponses;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PasswordHistoryRepository passwordHistory;
    private final PasswordService passwordService;
    private final RefreshTokenService refreshTokenService;
    private final AuditLog audit;

    public UserService(UserRepository users, RoleRepository roles, UserRoleRepository userRoles,
                       PasswordHistoryRepository passwordHistory, PasswordService passwordService,
                       RefreshTokenService refreshTokenService, AuditLog audit) {
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.passwordHistory = passwordHistory;
        this.passwordService = passwordService;
        this.refreshTokenService = refreshTokenService;
        this.audit = audit;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, String actor) {
        String username = request.username();
        String email = request.email().trim().toLowerCase();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("Username already taken");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already registered");
        }
        passwordService.validatePolicy(request.password());
        String hash = passwordService.encode(request.password());
        User user = User.create(username, email, request.displayName(), hash, request.role(), true);
        applyInitialStatus(user, request.effectiveStatus());
        users.save(user);
        mirrorRole(user);
        passwordHistory.save(PasswordHistory.record(user, hash));
        audit.log("USER_CREATED", actor, null, "SUCCESS",
                "created user " + username + " role=" + user.getRole() + " status=" + request.effectiveStatus());
        return UserResponses.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserResponses.from(find(id));
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request, String actor) {
        User user = find(id);
        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase();
            if (!email.equalsIgnoreCase(user.getEmail()) && users.existsByEmailIgnoreCase(email)) {
                throw new ConflictException("Email already registered");
            }
            user.setEmail(email);
        }
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName());
        }
        if (request.role() != null && request.role() != user.getRole()) {
            user.setRole(request.role());
            mirrorRole(user);
        }
        if (request.status() != null) {
            applyStatus(user, request.status());
        }
        users.save(user);
        audit.log("USER_UPDATED", actor, null, "SUCCESS", "updated user " + user.getUsername());
        return UserResponses.from(user);
    }

    @Transactional
    public void disable(UUID id, String actor) {
        User user = find(id);
        user.setEnabled(false);
        user.setLockoutUntil(null);
        users.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
        audit.log("USER_DISABLED", actor, null, "SUCCESS", "disabled user " + user.getUsername());
    }

    @Transactional
    public void remove(UUID id, String actor) {
        User user = find(id);
        user.setEnabled(false);
        user.setLockoutUntil(null);
        users.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
        audit.log("USER_DELETED", actor, null, "SUCCESS", "soft-deleted user " + user.getUsername());
    }

    private User find(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private void mirrorRole(User user) {
        userRoles.deleteByUserId(user.getId());
        roles.findByName(user.getRole().name()).ifPresent(role ->
                userRoles.save(new UserRole(user.getId(), role.getId())));
    }

    private void applyInitialStatus(User user, Status status) {
        switch (status) {
            case ACTIVE -> user.setEnabled(true);
            case INACTIVE -> user.setEnabled(false);
            case LOCKED -> {
                user.setEnabled(true);
                user.setLockoutUntil(Instant.now().plus(Duration.ofDays(365)));
            }
            default -> user.setEnabled(true);
        }
    }

    private void applyStatus(User user, Status status) {
        switch (status) {
            case ACTIVE -> {
                user.setEnabled(true);
                user.setLockoutUntil(null);
                user.setFailedLoginAttempts(0);
            }
            case INACTIVE -> {
                user.setEnabled(false);
                user.setLockoutUntil(null);
            }
            case LOCKED -> {
                user.setEnabled(true);
                user.setLockoutUntil(Instant.now().plus(Duration.ofDays(365)));
            }
            default -> user.setEnabled(true);
        }
    }
}