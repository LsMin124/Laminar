package com.laminar.perpetual;

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
@Table(name = "perpetual_versions")
@Filter(
    name = "personalFirstFilter",
    condition = "workspace_id = :ctxWorkspaceId and user_id = :ctxUserId")
@Getter
@Setter
public class PerpetualVersionEntity extends PersonalBaseEntity {

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "perpetual_note_id", nullable = false)
  private UUID perpetualNoteId;

  @Column(name = "card_id")
  private UUID cardId;

  @Column(name = "version_number", nullable = false)
  private int versionNumber;

  @Column(name = "summary")
  private String summary;

  @Column(name = "body_diff_md")
  private String bodyDiffMd;

  @Column(name = "is_current_diff", nullable = false)
  private boolean currentDiff;

  @Column(name = "committed_at", nullable = false)
  private OffsetDateTime committedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
