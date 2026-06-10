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

  // append 시 서비스가 app clock으로 채운다(updatable=false = append-only 불변).
  // 과거 insertable=false(DB default now() 의존)는 ① 반환 엔티티의 occurredAt이 null이고
  // ② 한 트랜잭션 안 now()가 고정이라 연속 append의 desc 정렬이 불정이었다. DB default는 안전망으로 잔존.
  @Column(name = "occurred_at", nullable = false, updatable = false)
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
