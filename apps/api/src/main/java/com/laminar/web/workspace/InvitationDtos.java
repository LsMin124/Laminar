package com.laminar.web.workspace;

import com.laminar.workspace.WorkspaceRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public final class InvitationDtos {

    private InvitationDtos() {
    }

    public record InviteRequest(
            @Email @NotBlank String email,
            @NotNull WorkspaceRole role
    ) {
    }

    public record InviteResponse(
            UUID invitationId,
            String rawToken,
            String email,
            WorkspaceRole role
    ) {
    }

    public record AcceptRequest(@NotBlank String token) {
    }
}
