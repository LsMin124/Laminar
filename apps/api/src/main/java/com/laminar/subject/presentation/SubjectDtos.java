package com.laminar.subject.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** /api/workspaces/** 요청·응답 DTO 모음. */
public final class SubjectDtos {

  private SubjectDtos() {}

  public record CreateRequest(
      @NotBlank @Size(max = 200) String name,
      @NotBlank @Size(max = 50) String slug,
      @Size(max = 60) String defaultTimezone) {}

  public record UpdateRequest(
      @Size(max = 200) String name,
      @Size(max = 60) String defaultTimezone,
      Map<String, Object> settings) {}

  public record SubjectResponse(
      UUID id,
      String name,
      String slug,
      UUID ownerUserId,
      String defaultTimezone,
      Map<String, Object> settings,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}
}
