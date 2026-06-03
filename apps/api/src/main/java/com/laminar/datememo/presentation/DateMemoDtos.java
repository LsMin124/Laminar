package com.laminar.datememo.presentation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class DateMemoDtos {

  private DateMemoDtos() {}

  public record UpsertRequest(
      @NotNull UUID tabId,
      @NotNull LocalDate date,
      @Size(max = 10000) String bodyMd,
      Map<String, Object> attrs) {}

  public record DateMemoResponse(
      UUID tabId,
      UUID userId,
      LocalDate date,
      String bodyMd,
      Map<String, Object> attrs,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}
}
