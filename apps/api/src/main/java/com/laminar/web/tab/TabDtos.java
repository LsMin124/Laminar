package com.laminar.web.tab;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TabDtos {

  private TabDtos() {}

  public record CreateRequest(
      @NotNull UUID boardId,
      UUID parentTabId,
      @NotBlank @Size(max = 200) String name,
      Boolean visible,
      Boolean collapsed,
      Boolean showLabel,
      @Size(max = 30) String labelColor,
      Map<String, Object> attrs) {}

  public record UpdateRequest(
      @Size(max = 200) String name,
      UUID parentTabId,
      Boolean visible,
      Boolean collapsed,
      Boolean showLabel,
      @Size(max = 30) String labelColor,
      Map<String, Object> attrs) {}

  public record ReorderRequest(@NotNull UUID boardId, @NotEmpty List<UUID> orderedIds) {}

  public record TabResponse(
      UUID id,
      UUID workspaceId,
      UUID userId,
      UUID boardId,
      UUID parentTabId,
      String name,
      int priority,
      boolean visible,
      boolean collapsed,
      boolean showLabel,
      String labelColor,
      Map<String, Object> attrs,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}
}
