package com.laminar.samplemanager.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class SampleManagerLinkDtos {

  private SampleManagerLinkDtos() {}

  public record LinkOrUpdateRequest(
      @NotNull UUID cardId,
      @NotBlank @Size(max = 200) String sampleId,
      @NotBlank @Size(max = 200) String stepId,
      @Size(max = 1000) String sampleManagerUrl,
      Map<String, Object> payloadSnapshot) {}

  public record LinkResponse(
      UUID id,
      UUID subjectId,
      UUID userId,
      UUID cardId,
      String sampleId,
      String stepId,
      String sampleManagerUrl,
      OffsetDateTime syncedAt,
      Map<String, Object> payloadSnapshot,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}
}
