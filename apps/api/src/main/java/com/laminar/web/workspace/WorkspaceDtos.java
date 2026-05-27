package com.laminar.web.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * /api/workspaces/** 요청·응답 DTO 모음.
 */
public final class WorkspaceDtos {

    private WorkspaceDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 50) String slug,
            @Size(max = 60) String defaultTimezone
    ) {
    }

    public record UpdateRequest(
            @Size(max = 200) String name,
            @Size(max = 60) String defaultTimezone,
            Map<String, Object> settings
    ) {
    }

    public record WorkspaceResponse(
            UUID id,
            String name,
            String slug,
            UUID ownerUserId,
            String defaultTimezone,
            Map<String, Object> settings,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
