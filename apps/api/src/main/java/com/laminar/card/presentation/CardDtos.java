package com.laminar.card.presentation;

import com.laminar.card.domain.CardImportance;
import com.laminar.card.domain.CardOrigin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CardDtos {

  private CardDtos() {}

  public record CreateRequest(
      UUID boardId,
      @NotBlank @Size(max = 200) String title,
      @Size(max = 100) String slug,
      String bodyMd,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime startTime,
      Boolean allDay,
      @Size(max = 60) String timeZone,
      CardImportance importance,
      @Size(max = 500) String rrule,
      CardOrigin origin,
      Map<String, Object> attrs) {}

  public record UpdateRequest(
      @Size(max = 200) String title,
      String bodyMd,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime startTime,
      Boolean allDay,
      @Size(max = 60) String timeZone,
      CardImportance importance,
      @Size(max = 500) String rrule,
      Boolean completed,
      Map<String, Object> attrs) {}

  public record ReorderRequest(@NotEmpty List<UUID> orderedIds) {}

  public record RenderedBodyResponse(UUID cardId, String html) {}

  public record CardResponse(
      UUID id,
      UUID workspaceId,
      UUID userId,
      UUID boardId,
      String title,
      String slug,
      String bodyMd,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime startTime,
      boolean allDay,
      String timeZone,
      CardImportance importance,
      boolean completed,
      String rrule,
      CardOrigin origin,
      int priority,
      Map<String, Object> attrs,
      OffsetDateTime archivedAt,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}
}
