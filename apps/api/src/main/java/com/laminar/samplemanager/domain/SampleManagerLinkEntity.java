package com.laminar.samplemanager.domain;

import com.laminar.common.domain.PersonalBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sample_manager_links")
@Filter(
    name = "personalFirstFilter",
    condition = "subject_id = :ctxSubjectId and user_id = :ctxUserId")
@Getter
@Setter
public class SampleManagerLinkEntity extends PersonalBaseEntity {

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "card_id", nullable = false)
  private UUID cardId;

  @Column(name = "sample_id", nullable = false)
  private String sampleId;

  @Column(name = "step_id", nullable = false)
  private String stepId;

  @Column(name = "sample_manager_url")
  private String sampleManagerUrl;

  @Column(name = "synced_at")
  private OffsetDateTime syncedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_snapshot", columnDefinition = "jsonb")
  private Map<String, Object> payloadSnapshot;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
