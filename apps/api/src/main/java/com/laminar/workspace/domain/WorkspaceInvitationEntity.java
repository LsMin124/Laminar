package com.laminar.workspace.domain;

import com.laminar.common.domain.WorkspaceScopedBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "workspace_invitations")
@Filter(name = "workspaceSharedFilter", condition = "workspace_id = :ctxWorkspaceId")
@Getter
@Setter
public class WorkspaceInvitationEntity extends WorkspaceScopedBaseEntity {

  @Column(name = "email", nullable = false, columnDefinition = "citext")
  private String email;

  @Column(name = "role", nullable = false)
  private WorkspaceRole role;

  @Column(name = "invited_by", nullable = false)
  private UUID invitedBy;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "accepted_at")
  private OffsetDateTime acceptedAt;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;
}
