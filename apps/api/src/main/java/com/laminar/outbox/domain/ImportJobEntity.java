package com.laminar.outbox.domain;

import com.laminar.common.domain.PersonalBaseEntity;
import com.laminar.common.domain.SoftDeletable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "import_jobs")
@Filter(
    name = "personalFirstFilter",
    condition = "subject_id = :ctxSubjectId and user_id = :ctxUserId")
@Getter
@Setter
public class ImportJobEntity extends PersonalBaseEntity implements SoftDeletable {

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "status", nullable = false)
  private ImportJobStatus status = ImportJobStatus.PENDING;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "progress", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> progress = new HashMap<>();

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "import_token")
  private String importToken;

  @Column(name = "started_at")
  private OffsetDateTime startedAt;

  @Column(name = "finished_at")
  private OffsetDateTime finishedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
