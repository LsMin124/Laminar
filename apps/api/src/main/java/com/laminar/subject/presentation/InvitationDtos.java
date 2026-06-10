package com.laminar.subject.presentation;

import com.laminar.context.SubjectRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public final class InvitationDtos {

  private InvitationDtos() {}

  public record InviteRequest(@Email @NotBlank String email, @NotNull SubjectRole role) {}

  public record InviteResponse(
      UUID invitationId, String rawToken, String email, SubjectRole role) {}

  public record AcceptRequest(@NotBlank String token) {}
}
