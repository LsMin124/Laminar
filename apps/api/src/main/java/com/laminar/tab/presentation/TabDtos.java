package com.laminar.tab.presentation;

import com.laminar.tab.domain.TabDefaultView;
import com.laminar.tab.domain.TabKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TabDtos {

  private TabDtos() {}

  public record CreateRequest(
      @NotBlank @Size(max = 200) String name,
      @NotBlank @Size(max = 100) String slug,
      TabKind kind,
      TabDefaultView defaultView,
      @Size(max = 100) String iconName,
      @Size(max = 30) String iconColor,
      Map<String, Object> settings) {}

  public record UpdateRequest(
      @Size(max = 200) String name,
      TabDefaultView defaultView,
      @Size(max = 100) String iconName,
      @Size(max = 30) String iconColor,
      @Size(max = 100000) String bodyMd,
      Map<String, Object> settings) {}

  public record ReorderRequest(@NotEmpty List<UUID> orderedIds) {}

  public record TabResponse(
      UUID id,
      UUID subjectId,
      UUID userId,
      String name,
      String slug,
      TabKind kind,
      TabDefaultView defaultView,
      String iconName,
      String iconColor,
      String bodyMd,
      Map<String, Object> settings,
      int priority,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}
}
