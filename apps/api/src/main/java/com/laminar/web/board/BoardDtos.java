package com.laminar.web.board;

import com.laminar.board.BoardDefaultView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class BoardDtos {

    private BoardDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 100) String slug,
            BoardDefaultView defaultView,
            @Size(max = 100) String iconName,
            @Size(max = 30) String iconColor,
            Map<String, Object> settings
    ) {
    }

    public record UpdateRequest(
            @Size(max = 200) String name,
            BoardDefaultView defaultView,
            @Size(max = 100) String iconName,
            @Size(max = 30) String iconColor,
            Map<String, Object> settings
    ) {
    }

    public record BoardResponse(
            UUID id,
            UUID workspaceId,
            UUID userId,
            String name,
            String slug,
            BoardDefaultView defaultView,
            String iconName,
            String iconColor,
            Map<String, Object> settings,
            int priority,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
