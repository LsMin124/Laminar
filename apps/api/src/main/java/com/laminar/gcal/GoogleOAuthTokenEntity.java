package com.laminar.gcal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "google_oauth_tokens")
public class GoogleOAuthTokenEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "access_token_enc")
    private byte[] accessTokenEnc;

    @Column(name = "refresh_token_enc")
    private byte[] refreshTokenEnc;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "scope")
    private String scope;

    @Column(name = "key_version", nullable = false)
    private int keyVersion = 1;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public byte[] getAccessTokenEnc() { return accessTokenEnc; }
    public void setAccessTokenEnc(byte[] accessTokenEnc) { this.accessTokenEnc = accessTokenEnc; }

    public byte[] getRefreshTokenEnc() { return refreshTokenEnc; }
    public void setRefreshTokenEnc(byte[] refreshTokenEnc) { this.refreshTokenEnc = refreshTokenEnc; }

    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public int getKeyVersion() { return keyVersion; }
    public void setKeyVersion(int keyVersion) { this.keyVersion = keyVersion; }

    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GoogleOAuthTokenEntity that)) return false;
        return userId != null && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return userId != null ? userId.hashCode() : getClass().hashCode();
    }
}
