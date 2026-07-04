package com.laminar.whiteboard.domain;

import com.laminar.common.domain.PersonalBaseEntity;
import com.laminar.common.domain.SoftDeletable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 화이트보드 노드 — 자유 배치 md·이미지 엔티티(Personal-First 격리). x/y는 캔버스 월드 좌표(px)로 직접 저장한다 — 카드의 x=시간축 파생과 달리 자유
 * 좌표라 역변환이 없다(계획 재사용 노트: useDagDrag 커밋만 x=날짜 제거).
 */
@Entity
@Table(name = "whiteboard_nodes")
@Filter(
    name = "personalFirstFilter",
    condition = "subject_id = :ctxSubjectId and user_id = :ctxUserId")
@Getter
@Setter
public class WhiteboardNodeEntity extends PersonalBaseEntity implements SoftDeletable {

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "tab_id", nullable = false)
  private UUID tabId;

  @Column(name = "kind", nullable = false)
  private WhiteboardNodeKind kind;

  @Column(name = "x", nullable = false)
  private double x;

  @Column(name = "y", nullable = false)
  private double y;

  @Column(name = "width")
  private Double width;

  @Column(name = "height")
  private Double height;

  /** 노드 제목/라벨 — md=헤드라인, image=캡션. */
  @Column(name = "text")
  private String text;

  @Column(name = "body_md")
  private String bodyMd;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "attrs", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attrs = new HashMap<>();

  @jakarta.persistence.Version
  @Column(name = "version", nullable = false)
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private long version;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
