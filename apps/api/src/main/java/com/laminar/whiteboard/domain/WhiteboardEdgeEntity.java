package com.laminar.whiteboard.domain;

import com.laminar.common.domain.PersonalBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 화이트보드 엣지 — 독립 캔버스 노드 간 자유 연결 (Personal-First). */
@Entity
@Table(name = "whiteboard_edges")
@Filter(
    name = "personalFirstFilter",
    condition = "workspace_id = :ctxWorkspaceId and user_id = :ctxUserId")
@Getter
@Setter
public class WhiteboardEdgeEntity extends PersonalBaseEntity {

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "board_id", nullable = false)
  private UUID boardId;

  @Column(name = "from_node_id", nullable = false)
  private UUID fromNodeId;

  @Column(name = "to_node_id", nullable = false)
  private UUID toNodeId;

  @Column(name = "label")
  private String label;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "attrs", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attrs = new HashMap<>();
}
