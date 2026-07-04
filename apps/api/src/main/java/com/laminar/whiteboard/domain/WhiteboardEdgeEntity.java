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
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 화이트보드 노드 사이 관계 화살표 (card_relations 미러). DAG의 시간 강제·비순환이 없다 — 순수 관계 시각화라 사이클을 허용한다. {@code label}이
 * 곧 화살표가 나타내는 관계(별도 분류 없음).
 */
@Entity
@Table(name = "whiteboard_edges")
@Filter(
    name = "personalFirstFilter",
    condition = "subject_id = :ctxSubjectId and user_id = :ctxUserId")
@Getter
@Setter
public class WhiteboardEdgeEntity extends PersonalBaseEntity implements SoftDeletable {

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "tab_id", nullable = false)
  private UUID tabId;

  @Column(name = "from_node_id", nullable = false)
  private UUID fromNodeId;

  @Column(name = "to_node_id", nullable = false)
  private UUID toNodeId;

  @Column(name = "relation_kind", nullable = false)
  private String relationKind = "default";

  @Column(name = "label")
  private String label;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "attrs", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attrs = new HashMap<>();

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
