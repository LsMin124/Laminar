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

/** 화이트보드 노드 — 타임라인/캘린더와 무관한 독립 캔버스의 자유 노드 (Personal-First). */
@Entity
@Table(name = "whiteboard_nodes")
@Filter(
    name = "personalFirstFilter",
    condition = "workspace_id = :ctxWorkspaceId and user_id = :ctxUserId")
@Getter
@Setter
public class WhiteboardNodeEntity extends PersonalBaseEntity {

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "board_id", nullable = false)
  private UUID boardId;

  @Column(name = "text", nullable = false)
  private String text = "";

  @Column(name = "x", nullable = false)
  private double x;

  @Column(name = "y", nullable = false)
  private double y;

  @Column(name = "width", nullable = false)
  private double width = 180;

  @Column(name = "height", nullable = false)
  private double height = 88;

  @Column(name = "color")
  private String color;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "attrs", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attrs = new HashMap<>();
}
