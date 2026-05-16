package com.helpagent.action.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "oauth_tokens")
public class OAuthTokenEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "username")
    private String username;

    @Column(name = "access_token", nullable = false, length = 512)
    private String accessToken;

    @Column(name = "token_type")
    private String tokenType;

    @Column(name = "scope")
    private String scope;

    @Column(name = "refresh_token", length = 512)
    private String refreshToken;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OAuthTokenEntity() {}

    public OAuthTokenEntity(String userId, String username, String accessToken,
                            String tokenType, String scope, String refreshToken, Instant expiresAt) {
        this.userId = userId;
        this.username = username;
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.scope = scope;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}