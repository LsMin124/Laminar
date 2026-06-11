package com.laminar.subject.presentation;

import com.laminar.context.SubjectKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** /api/subjects/** 요청·응답 DTO 모음. kind는 enum name(PERSONAL|LAB)으로 직렬화 — FE Subject.kind 거울. */
public final class SubjectDtos {

  private SubjectDtos() {}

  public record CreateRequest(
      @NotBlank @Size(max = 200) String name,
      @NotBlank @Size(max = 50) String slug,
      @Size(max = 60) String defaultTimezone) {}

  public record UpdateRequest(
      @Size(max = 200) String name,
      @Size(max = 60) String defaultTimezone,
      @Size(max = 100000) String bodyMd,
      Map<String, Object> settings) {}

  public record SubjectResponse(
      UUID id,
      String name,
      String slug,
      UUID ownerUserId,
      SubjectKind kind,
      String defaultTimezone,
      String bodyMd,
      Map<String, Object> settings,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}
}
