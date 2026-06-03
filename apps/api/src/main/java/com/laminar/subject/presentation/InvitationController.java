package com.laminar.subject.presentation;

import com.laminar.context.SubjectContextHolder;
import com.laminar.security.LaminarPrincipal;
import com.laminar.subject.application.InvitationService;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/subjects/current/invitations + /api/auth/invitations/accept.
 *
 * <p>invite는 워크스페이스 진입 후 (PERSONAL scope) OWNER/MEMBER만 (canWrite). accept는 인증 후 SYSTEM scope에서 호출
 * (워크스페이스 미선택 상태에서 토큰 제출).
 */
@RestController
public class InvitationController {

  private final InvitationService invitationService;

  public InvitationController(InvitationService invitationService) {
    this.invitationService = invitationService;
  }

  @PostMapping("/api/subjects/current/invitations")
  public ResponseEntity<InvitationDtos.InviteResponse> invite(
      Authentication authentication, @Valid @RequestBody InvitationDtos.InviteRequest request) {
    LaminarPrincipal principal = requirePrincipal(authentication);
    UUID subjectId = SubjectContextHolder.require().subjectId();
    if (subjectId == null) {
      throw new IllegalStateException("subject context required");
    }
    if (!SubjectContextHolder.require().canWrite()) {
      return ResponseEntity.status(403).build();
    }
    InvitationService.InvitationIssue issue =
        invitationService.invite(subjectId, request.email(), request.role(), principal.userId());
    return ResponseEntity.ok(
        new InvitationDtos.InviteResponse(
            issue.invitationId(), issue.rawToken(), request.email(), request.role()));
  }

  @GetMapping("/api/subjects/current/invitations")
  public ResponseEntity<List<PendingInvitationResponse>> listPending() {
    return ResponseEntity.ok(
        invitationService.listPendingForCurrentSubject().stream()
            .map(
                i ->
                    new PendingInvitationResponse(
                        i.getId(),
                        i.getEmail(),
                        i.getRole().name(),
                        i.getInvitedBy(),
                        i.getExpiresAt(),
                        i.getCreatedAt()))
            .toList());
  }

  @DeleteMapping("/api/subjects/current/invitations/{invitationId}")
  public ResponseEntity<Void> revoke(@PathVariable UUID invitationId) {
    if (!SubjectContextHolder.require().canWrite()) {
      return ResponseEntity.status(403).build();
    }
    invitationService.revoke(invitationId);
    return ResponseEntity.noContent().build();
  }

  public record PendingInvitationResponse(
      UUID id,
      String email,
      String role,
      UUID invitedBy,
      OffsetDateTime expiresAt,
      OffsetDateTime createdAt) {}

  @PostMapping("/api/auth/invitations/accept")
  public ResponseEntity<AcceptResponse> accept(
      @Valid @RequestBody InvitationDtos.AcceptRequest request) {
    return invitationService
        .accept(request.token())
        .map(
            m ->
                ResponseEntity.ok(new AcceptResponse(m.getId().getSubjectId(), m.getRole().name())))
        .orElseGet(() -> ResponseEntity.status(404).build());
  }

  private LaminarPrincipal requirePrincipal(Authentication authentication) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof LaminarPrincipal principal)) {
      throw new IllegalStateException("authentication required");
    }
    return principal;
  }

  public record AcceptResponse(UUID subjectId, String role) {}
}
