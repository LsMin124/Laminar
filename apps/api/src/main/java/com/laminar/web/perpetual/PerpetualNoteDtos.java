package com.laminar.web.perpetual;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PerpetualNoteDtos {

  private PerpetualNoteDtos() {}

  public record CreateRequest(
      UUID boardId,
      UUID tabId,
      UUID parentPerpetualId,
      @NotBlank @Size(max = 200) String title,
      @Size(max = 100000) String bodyMd,
      Map<String, Object> attrs) {}

  public record UpdateRequest(
      @Size(max = 200) String title,
      @Size(max = 100000) String bodyMd,
      UUID parentPerpetualId,
      Map<String, Object> attrs) {}

  public record ReorderRequest(UUID tabId, @NotEmpty List<UUID> orderedIds) {}

  public record PerpetualNoteResponse(
      UUID id,
      UUID workspaceId,
      UUID userId,
      UUID boardId,
      UUID tabId,
      UUID parentPerpetualId,
      String title,
      String bodyMd,
      int priority,
      Map<String, Object> attrs,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}
}
