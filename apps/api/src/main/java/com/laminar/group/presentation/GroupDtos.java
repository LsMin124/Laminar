package com.laminar.group.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GroupDtos {

  private GroupDtos() {}

  public record CreateRequest(
      @NotNull UUID tabId,
      @NotBlank @Size(max = 200) String name,
      @Size(max = 30) String color,
      Map<String, Object> attrs) {}

  public record UpdateRequest(
      @Size(max = 200) String name, @Size(max = 30) String color, Map<String, Object> attrs) {}

  public record ReorderRequest(@NotNull UUID tabId, @NotEmpty List<UUID> orderedIds) {}

  public record GroupResponse(
      UUID id,
      UUID subjectId,
      UUID userId,
      UUID tabId,
      String name,
      String color,
      int priority,
      Map<String, Object> attrs,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}
}
