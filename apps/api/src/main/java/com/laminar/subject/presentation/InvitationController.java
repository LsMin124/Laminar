package com.laminar.subject.presentation;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.context.SubjectRole;
import com.laminar.error.ForbiddenException;
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
 * <p>invite·revoke는 워크스페이스 진입 후 ADMIN+만(LAB재설계 §1.3 — 초대 발급이 곧 가입 승인 행위이므로 관리자 전용). 부여 역할 차등: OWNER
 * 역할 초대는 항상 금지, ADMIN 역할 초대는 OWNER만(임명 권한), ADMIN은 MEMBER 초대만. accept는 인증 후 SYSTEM scope에서
 * 호출(워크스페이스 미선택 상태에서 토큰 제출).
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
    SubjectContext ctx = SubjectContextHolder.require();
    UUID subjectId = ctx.subjectId();
    if (subjectId == null) {
      throw new IllegalStateException("subject context required");
    }
    if (!ctx.isAdmin()) {
      throw new ForbiddenException("초대는 관리자만 가능합니다");
    }
    // 부여 역할 차등(§1.3): OWNER 초대 금지, ADMIN 역할 부여는 OWNER만.
    if (request.role() == SubjectRole.OWNER
        || (request.role() == SubjectRole.ADMIN && !ctx.isOwner())) {
      throw new ForbiddenException("해당 역할을 부여할 권한이 없습니다");
    }
    InvitationService.InvitationIssue issue =
        invitationService.invite(subjectId, request.email(), request.role(), principal.userId());
    return ResponseEntity.ok(
        new InvitationDtos.InviteResponse(
            issue.invitationId(), issue.rawToken(), request.email(), request.role()));
  }

  @GetMapping("/api/subjects/current/invitations")
  public ResponseEntity<List<PendingInvitationResponse>> listPending() {
    // Q3: 대기 초대 목록은 초대 대상 이메일·역할을 노출 — invite/revoke와 동일하게 ADMIN+ 전용.
    if (!SubjectContextHolder.require().isAdmin()) {
      throw new ForbiddenException("초대 목록은 관리자만 조회할 수 있습니다");
    }
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
    if (!SubjectContextHolder.require().isAdmin()) {
      throw new ForbiddenException("초대 회수는 관리자만 가능합니다");
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
