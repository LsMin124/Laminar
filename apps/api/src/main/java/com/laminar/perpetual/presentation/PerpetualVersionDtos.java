package com.laminar.perpetual.presentation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class PerpetualVersionDtos {

  private PerpetualVersionDtos() {}

  public record CommitRequest(
      @NotNull UUID perpetualNoteId,
      UUID cardId,
      @Size(max = 500) String summary,
      @Size(max = 100000) String bodyDiffMd,
      boolean markCurrent) {}

  public record VersionResponse(
      UUID id,
      UUID workspaceId,
      UUID userId,
      UUID perpetualNoteId,
      UUID cardId,
      int versionNumber,
      String summary,
      String bodyDiffMd,
      boolean currentDiff,
      OffsetDateTime committedAt,
      OffsetDateTime createdAt) {}
}
