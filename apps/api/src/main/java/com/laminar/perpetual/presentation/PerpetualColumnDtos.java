package com.laminar.perpetual.presentation;

import com.laminar.perpetual.domain.PerpetualColumnType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PerpetualColumnDtos {

  private PerpetualColumnDtos() {}

  public record CreateDefinitionRequest(
      @NotNull UUID boardId,
      @NotBlank @Size(max = 100) String name,
      @NotNull PerpetualColumnType type,
      List<String> enumValues) {}

  public record DefinitionResponse(
      UUID id,
      UUID workspaceId,
      UUID boardId,
      String name,
      PerpetualColumnType type,
      List<String> enumValues,
      int priority,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}

  public record UpsertValueRequest(
      @NotNull UUID perpetualNoteId, @NotNull UUID columnDefinitionId, String value) {}

  public record ColumnValueResponse(UUID perpetualNoteId, UUID columnDefinitionId, String value) {}
}
