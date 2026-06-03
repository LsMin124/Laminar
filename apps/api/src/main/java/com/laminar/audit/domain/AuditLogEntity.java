package com.laminar.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_log")
@Filter(name = "subjectSharedFilter", condition = "subject_id = :ctxSubjectId")
@Getter
@Setter
public class AuditLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "subject_id", nullable = false)
  private UUID subjectId;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Column(name = "action", nullable = false)
  private String action;

  @Column(name = "target_type")
  private String targetType;

  @Column(name = "target_id")
  private UUID targetId;

  @Column(name = "summary")
  private String summary;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payload = new HashMap<>();

  @Column(name = "occurred_at", nullable = false, insertable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private OffsetDateTime occurredAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AuditLogEntity that)) return false;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : getClass().hashCode();
  }
}
