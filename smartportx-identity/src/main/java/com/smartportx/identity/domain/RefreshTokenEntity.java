package com.smartportx.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens", schema = "smartportx_identity")
public class RefreshTokenEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(nullable = false)
    private boolean revoked;

    protected RefreshTokenEntity() {
    }

    public static RefreshTokenEntity issue(User user, UUID familyId, String tokenHash, Instant expiresAt) {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.id = UUID.randomUUID();
        token.user = user;
        token.familyId = familyId == null ? UUID.randomUUID() : familyId;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        token.createdAt = Instant.now();
        return token;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isReused() {
        return revoked || usedAt != null;
    }

    public boolean isExpired(Instant reference) {
        return expiresAt.isBefore(reference);
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void markUsed(Instant when) {
        this.usedAt = when;
    }

    public void revoke() {
        this.revoked = true;
    }
}