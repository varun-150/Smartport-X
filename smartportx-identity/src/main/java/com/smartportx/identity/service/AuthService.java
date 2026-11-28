package com.smartportx.identity.service;

import com.smartportx.identity.config.SecurityProperties;
import com.smartportx.identity.domain.RefreshTokenEntity;
import com.smartportx.identity.domain.User;
import com.smartportx.identity.error.CredentialsException;
import com.smartportx.identity.error.PasswordPolicyViolation;
import com.smartportx.identity.error.RateLimitException;
import com.smartportx.identity.repository.PasswordHistoryRepository;
import com.smartportx.identity.repository.RefreshTokenRepository;
import com.smartportx.identity.repository.UserRepository;
import com.smartportx.identity.security.AuditLog;
import com.smartportx.identity.security.JwtTokenService;
import com.smartportx.identity.security.LoginRateLimiter;
import com.smartportx.identity.security.TokenHasher;
import com.smartportx.identity.web.dto.ChangePasswordRequest;
import com.smartportx.identity.web.dto.LoginRequest;
import com.smartportx.identity.web.dto.LoginResponse;
import com.smartportx.identity.web.dto.RefreshResponse;
import com.smartportx.identity.web.dto.UserResponses;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordHistoryRepository passwordHistory;
    private final RefreshTokenRepository tokens;
    private final JwtTokenService jwt;
    private final RefreshTokenService refreshTokenService;
    private final AccountSecurity accountSecurity;
    private final PasswordService passwordService;
    private final LoginRateLimiter loginRateLimiter;
    private final SecurityProperties properties;
    private final AuditLog audit;

    public AuthService(UserRepository users, PasswordHistoryRepository passwordHistory,
                       RefreshTokenRepository tokens, JwtTokenService jwt,
                       RefreshTokenService refreshTokenService, AccountSecurity accountSecurity,
                       PasswordService passwordService, LoginRateLimiter loginRateLimiter,
                       SecurityProperties properties, AuditLog audit) {
        this.users = users;
        this.passwordHistory = passwordHistory;
        this.tokens = tokens;
        this.jwt = jwt;
        this.refreshTokenService = refreshTokenService;
        this.accountSecurity = accountSecurity;
        this.passwordService = passwordService;
        this.loginRateLimiter = loginRateLimiter;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional(noRollbackFor = CredentialsException.class)
    public LoginResponse login(LoginRequest request, String sourceIp) {
        String username = request.username();
        if (!loginRateLimiter.tryAcquire(username)) {
            audit.log("LOGIN_RATE_LIMITED", username, sourceIp, "FAILED", "rate limit exceeded");
            throw new RateLimitException("Too many login attempts; try again shortly", 60);
        }
        User user = users.findByUsernameIgnoreCase(username).orElse(null);
        if (user == null || !user.isEnabled()) {
            audit.log("LOGIN_FAILED", username, sourceIp, "FAILED", "user absent or disabled");
            throw new CredentialsException("Invalid username or password");
        }
        if (user.isLocked(Instant.now())) {
            long seconds = Duration.between(Instant.now(), user.getLockoutUntil()).getSeconds();
            audit.log("LOGIN_LOCKED", username, sourceIp, "FAILED", "account locked until " + user.getLockoutUntil());
            throw new RateLimitException("Account temporarily locked", (int) Math.max(1, seconds));
        }
        if (!passwordService.matches(request.password(), user.getPasswordHash())) {
            accountSecurity.registerLoginFailure(user);
            audit.log("LOGIN_FAILED", username, sourceIp, "FAILED", "wrong password");
            throw new CredentialsException("Invalid username or password");
        }
        accountSecurity.resetLoginFailures(user);
        user.setLastLoginAt(Instant.now());
        users.save(user);
        audit.log("LOGIN", username, sourceIp, "SUCCESS", "authentication succeeded; role=" + user.getRole());
        return issueSession(user);
    }

    public LoginResponse issueSession(User user) {
        String access = jwt.accessToken(user);
        UUID familyId = UUID.randomUUID();
        JwtTokenService.TokenAndExpiry refresh = jwt.refreshToken(user, familyId);
        tokens.save(RefreshTokenEntity.issue(user, familyId, TokenHasher.sha256(refresh.token()), refresh.expiresAt()));
        return new LoginResponse(access, refresh.token(), jwt.accessTokenTtlSeconds(), "Bearer", UserResponses.from(user));
    }

    @Transactional
    public RefreshResponse refresh(String rawRefreshToken, String sourceIp) {
        RefreshTokenService.RefreshResult result = refreshTokenService.rotate(rawRefreshToken);
        audit.log("REFRESH", result.user().getUsername(), sourceIp, "SUCCESS", "access token reissued");
        return new RefreshResponse(result.accessToken(), result.refreshToken(), result.expiresInSeconds(), "Bearer");
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenService.revokeAllForUser(userId);
        audit.log("LOGOUT", userId.toString(), null, "SUCCESS", "all refresh tokens revoked");
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request, String actor) {
        User user = users.findById(userId)
                .orElseThrow(() -> new com.smartportx.common.error.NotFoundException("User not found"));
        if (!actor.equalsIgnoreCase(user.getUsername())) {
            throw new AccessDeniedException("Cannot change another user's password");
        }
        if (!passwordService.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new CredentialsException("Current password is incorrect");
        }
        passwordService.validatePolicy(request.newPassword());
        List<com.smartportx.identity.domain.PasswordHistory> recent =
                passwordHistory.findTop5ByUserIdOrderByCreatedAtDesc(user.getId());
        boolean reused = recent.stream()
                .anyMatch(h -> passwordService.matches(request.newPassword(), h.getPasswordHash()));
        if (reused) {
            throw new PasswordPolicyViolation(
                    "New password has been used recently; choose a password not used in the last 5 changes");
        }
        String hash = passwordService.encode(request.newPassword());
        user.setPasswordHash(hash);
        users.save(user);
        passwordHistory.save(com.smartportx.identity.domain.PasswordHistory.record(user, hash));
        refreshTokenService.revokeAllForUser(user.getId());
        audit.log("PASSWORD_CHANGED", actor, null, "SUCCESS", "password rotated; sessions revoked");
    }
}