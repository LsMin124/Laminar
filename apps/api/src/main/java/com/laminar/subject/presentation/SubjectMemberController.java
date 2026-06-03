package com.laminar.subject.presentation;

import com.laminar.context.SubjectContextHolder;
import com.laminar.security.LaminarPrincipal;
import com.laminar.subject.application.SubjectMemberService;
import com.laminar.subject.domain.SubjectMemberEntity;
import com.laminar.subject.domain.SubjectRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/workspaces/current/members — 멤버 조회/역할 변경/제거.
 *
 * <p>모두 워크스페이스 진입 후 호출 (X-Laminar-Subject-Id 헤더 필수). 변경 작업은 OWNER (canWrite) 만 — owner 제거 차단.
 */
@RestController
@RequestMapping("/api/workspaces/current/members")
public class SubjectMemberController {

  private final SubjectMemberService memberService;

  public SubjectMemberController(SubjectMemberService memberService) {
    this.memberService = memberService;
  }

  @GetMapping
  public ResponseEntity<List<MemberResponse>> list() {
    return ResponseEntity.ok(
        memberService.listCurrentMembers().stream()
            .map(SubjectMemberController::toResponse)
            .toList());
  }

  @PatchMapping("/{userId}/role")
  public ResponseEntity<MemberResponse> updateRole(
      Authentication authentication,
      @PathVariable UUID userId,
      @Valid @RequestBody UpdateRoleRequest request) {
    if (!SubjectContextHolder.require().canWrite()) {
      return ResponseEntity.status(403).build();
    }
    LaminarPrincipal principal = requirePrincipal(authentication);
    SubjectMemberEntity updated =
        memberService.updateRole(userId, request.role(), principal.userId());
    return ResponseEntity.ok(toResponseFromEntity(updated));
  }

  @DeleteMapping("/{userId}")
  public ResponseEntity<Void> remove(Authentication authentication, @PathVariable UUID userId) {
    if (!SubjectContextHolder.require().canWrite()) {
      return ResponseEntity.status(403).build();
    }
    LaminarPrincipal principal = requirePrincipal(authentication);
    memberService.removeMember(userId, principal.userId());
    return ResponseEntity.noContent().build();
  }

  public record MemberResponse(
      UUID subjectId,
      UUID userId,
      String email,
      String displayName,
      SubjectRole role,
      OffsetDateTime joinedAt) {}

  public record UpdateRoleRequest(@NotNull SubjectRole role) {}

  private static MemberResponse toResponse(SubjectMemberService.MemberView v) {
    return new MemberResponse(
        v.subjectId(), v.userId(), v.email(), v.displayName(), v.role(), v.joinedAt());
  }

  private static MemberResponse toResponseFromEntity(SubjectMemberEntity m) {
    return new MemberResponse(
        m.getId().getSubjectId(), m.getId().getUserId(), null, null, m.getRole(), m.getJoinedAt());
  }

  private LaminarPrincipal requirePrincipal(Authentication authentication) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof LaminarPrincipal principal)) {
      throw new IllegalStateException("authentication required");
    }
    return principal;
  }
}
