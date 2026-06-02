package com.laminar.web.card;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class CardRelationDtos {

  private CardRelationDtos() {}

  public record CreateRequest(
      @NotNull UUID fromCardId,
      @NotNull UUID toCardId,
      @Size(max = 50) String relationKind,
      @Size(max = 500) String summary,
      @Size(max = 100000) String bodyMd,
      Map<String, Object> attrs) {}

  public record CardRelationResponse(
      UUID id,
      UUID workspaceId,
      UUID userId,
      UUID boardId,
      UUID fromCardId,
      UUID toCardId,
      String relationKind,
      String summary,
      String bodyMd,
      Map<String, Object> attrs,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}
}
