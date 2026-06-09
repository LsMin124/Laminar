package com.laminar.group.presentation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class GroupRelationDtos {

  private GroupRelationDtos() {}

  public record CreateRequest(
      @NotNull UUID fromGroupId,
      @NotNull UUID toGroupId,
      @Size(max = 50) String relationKind,
      @Size(max = 500) String summary,
      @Size(max = 100000) String bodyMd,
      Map<String, Object> attrs) {}

  /** 엣지 라벨 수정 — summary가 곧 화살표의 관계(별도 분류 없음). null/빈 값은 라벨 제거. */
  public record UpdateRequest(@Size(max = 500) String summary) {}

  public record GroupRelationResponse(
      UUID id,
      UUID subjectId,
      UUID userId,
      UUID tabId,
      UUID fromGroupId,
      UUID toGroupId,
      String relationKind,
      String summary,
      String bodyMd,
      Map<String, Object> attrs,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}
}
