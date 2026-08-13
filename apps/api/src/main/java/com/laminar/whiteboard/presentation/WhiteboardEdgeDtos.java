package com.laminar.whiteboard.presentation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class WhiteboardEdgeDtos {

  private WhiteboardEdgeDtos() {}

  public record CreateRequest(
      @NotNull UUID fromNodeId,
      @NotNull UUID toNodeId,
      @Size(max = 50) String relationKind,
      @Size(max = 500) String label,
      Map<String, Object> attrs) {}

  /** 엣지 라벨 수정 — label이 곧 화살표의 관계. null/빈 값은 라벨 제거. */
  public record UpdateRequest(@Size(max = 500) String label) {}

  /** WB-D 끝점 재연결 — from/to 중 null은 무변경(한쪽만 끌어 옮기는 경우). */
  public record ReconnectRequest(UUID fromNodeId, UUID toNodeId) {}

  public record EdgeResponse(
      UUID id,
      UUID subjectId,
      UUID userId,
      UUID tabId,
      UUID fromNodeId,
      UUID toNodeId,
      String relationKind,
      String label,
      Map<String, Object> attrs,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}
}
