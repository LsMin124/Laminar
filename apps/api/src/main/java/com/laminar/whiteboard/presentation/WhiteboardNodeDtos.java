package com.laminar.whiteboard.presentation;

import com.laminar.whiteboard.domain.WhiteboardNodeKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class WhiteboardNodeDtos {

  private WhiteboardNodeDtos() {}

  public record CreateRequest(
      @NotNull UUID tabId,
      @NotNull WhiteboardNodeKind kind,
      @NotNull Double x,
      @NotNull Double y,
      Double width,
      Double height,
      @Size(max = 500) String text,
      @Size(max = 100000) String bodyMd,
      Map<String, Object> attrs) {}

  /** PATCH — null 필드는 무변경(이동만·리사이즈만 부분 patch 허용). */
  public record UpdateRequest(
      Double x,
      Double y,
      Double width,
      Double height,
      @Size(max = 500) String text,
      @Size(max = 100000) String bodyMd,
      Map<String, Object> attrs) {}

  public record NodeResponse(
      UUID id,
      UUID subjectId,
      UUID userId,
      UUID tabId,
      WhiteboardNodeKind kind,
      double x,
      double y,
      Double width,
      Double height,
      String text,
      String bodyMd,
      Map<String, Object> attrs,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}
}
