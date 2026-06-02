package com.laminar.web.whiteboard;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class WhiteboardDtos {

  private WhiteboardDtos() {}

  public record CreateNodeRequest(
      @Size(max = 2000) String text,
      double x,
      double y,
      Double width,
      Double height,
      @Size(max = 30) String color) {}

  public record UpdateNodeRequest(
      @Size(max = 2000) String text,
      Double x,
      Double y,
      Double width,
      Double height,
      @Size(max = 30) String color) {}

  public record CreateEdgeRequest(
      @NotNull UUID fromNodeId, @NotNull UUID toNodeId, @Size(max = 200) String label) {}

  public record NodeResponse(
      UUID id,
      UUID workspaceId,
      UUID userId,
      UUID boardId,
      String text,
      double x,
      double y,
      double width,
      double height,
      String color,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}

  public record EdgeResponse(
      UUID id,
      UUID workspaceId,
      UUID userId,
      UUID boardId,
      UUID fromNodeId,
      UUID toNodeId,
      String label,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}

  public record WhiteboardResponse(
      UUID boardId, List<NodeResponse> nodes, List<EdgeResponse> edges) {}
}
