package com.smartportx.identity.service;

import com.smartportx.identity.domain.RefreshTokenEntity;
import com.smartportx.identity.domain.User;
import com.smartportx.identity.error.CredentialsException;
import com.smartportx.identity.repository.RefreshTokenRepository;
import com.smartportx.identity.security.AuditLog;
import com.smartportx.identity.security.JwtTokenService;
import com.smartportx.identity.security.TokenHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository tokens;
    private final JwtTokenService jwt;
    private final AccountSecurity accountSecurity;
    private final AuditLog audit;

    public RefreshTokenService(RefreshTokenRepository tokens, JwtTokenService jwt,
                               AccountSecurity accountSecurity, AuditLog audit) {
        this.tokens = tokens;
        this.jwt = jwt;
        this.accountSecurity = accountSecurity;
        this.audit = audit;
    }

    @Transactional
    public RefreshResult rotate(String rawRefreshToken) {
        String hash = TokenHasher.sha256(rawRefreshToken.trim());
        RefreshTokenEntity stored = tokens.findByTokenHash(hash)
                .orElseThrow(() -> new CredentialsException("Invalid refresh token"));

        if (stored.isReused()) {
            tokens.revokeByFamily(stored.getFamilyId());
            User user = stored.getUser();
            accountSecurity.registerLoginFailure(user);
            audit.log("REFRESH_REUSE", user.getUsername(), null, "FAILED",
                    "reuse of rotated refresh token revoked family " + stored.getFamilyId());
            throw new CredentialsException("Refresh token reuse detected; session family revoked");
        }

        if (stored.isExpired(Instant.now())) {
            audit.log("REFRESH_EXPIRED", stored.getUser().getUsername(), null, "FAILED", "token expired");
            throw new CredentialsException("Refresh token expired");
        }

        stored.markUsed(Instant.now());
        tokens.save(stored);
        User user = stored.getUser();
        JwtTokenService.TokenAndExpiry next = jwt.refreshToken(user, stored.getFamilyId());
        tokens.save(RefreshTokenEntity.issue(user, stored.getFamilyId(), TokenHasher.sha256(next.token()), next.expiresAt()));
        String access = jwt.accessToken(user);
        audit.log("REFRESH", user.getUsername(), null, "SUCCESS", "token rotated within family " + stored.getFamilyId());
        return new RefreshResult(access, next.token(), jwt.accessTokenTtlSeconds(), user);
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        tokens.revokeByUser(userId);
    }

    @Transactional
    public void revokeFamily(UUID familyId) {
        tokens.revokeByFamily(familyId);
    }

    @Transactional
    public IssueResult issueForLogin(User user) {
        UUID familyId = UUID.randomUUID();
        JwtTokenService.TokenAndExpiry refresh = jwt.refreshToken(user, familyId);
        tokens.save(RefreshTokenEntity.issue(user, familyId, TokenHasher.sha256(refresh.token()), refresh.expiresAt()));
        String access = jwt.accessToken(user);
        audit.log("LOGIN", user.getUsername(), null, "SUCCESS", "issued tokens for family " + familyId);
        return new IssueResult(access, refresh.token(), jwt.accessTokenTtlSeconds(), familyId, user);
    }

    public record RefreshResult(String accessToken, String refreshToken, long expiresInSeconds, User user) {
    }

    public record IssueResult(String accessToken, String refreshToken, long expiresInSeconds,
                              UUID familyId, User user) {
    }
}