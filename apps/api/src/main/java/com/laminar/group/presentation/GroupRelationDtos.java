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
