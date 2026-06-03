package com.laminar.attachment.domain;

import com.laminar.common.domain.PersonalBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "attachments")
@Filter(
    name = "personalFirstFilter",
    condition = "workspace_id = :ctxWorkspaceId and user_id = :ctxUserId")
@Getter
@Setter
public class AttachmentEntity extends PersonalBaseEntity {

  @Column(name = "uploaded_by")
  private UUID uploadedBy;

  @Column(name = "parent_type", nullable = false)
  private AttachmentParentType parentType;

  @Column(name = "parent_id", nullable = false)
  private UUID parentId;

  @Column(name = "storage_key", nullable = false)
  private String storageKey;

  @Column(name = "original_name")
  private String originalName;

  @Column(name = "mime")
  private String mime;

  @Column(name = "size_bytes")
  private Long sizeBytes;

  @Column(name = "sha256")
  private String sha256;

  @Column(name = "access_check_required", nullable = false)
  private boolean accessCheckRequired = true;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
