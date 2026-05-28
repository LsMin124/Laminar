package com.laminar.web.tab;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class TabRelationDtos {

    private TabRelationDtos() {
    }

    public record CreateRequest(
            @NotNull UUID fromTabId,
            @NotNull UUID toTabId,
            @Size(max = 500) String summary,
            @Size(max = 10000) String bodyMd,
            Map<String, Object> attrs
    ) {
    }

    public record TabRelationResponse(
            UUID id,
            UUID workspaceId,
            UUID userId,
            UUID boardId,
            UUID fromTabId,
            UUID toTabId,
            String summary,
            String bodyMd,
            Map<String, Object> attrs,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
