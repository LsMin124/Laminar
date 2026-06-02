package com.laminar.gcal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "google_oauth_tokens")
@Getter
@Setter
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
  @Setter(AccessLevel.NONE)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private OffsetDateTime updatedAt;

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
