package com.laminar.workspace.presentation;

import com.laminar.context.WorkspaceContextHolder;
import com.laminar.security.LaminarPrincipal;
import com.laminar.workspace.application.WorkspaceMemberService;
import com.laminar.workspace.domain.WorkspaceMemberEntity;
import com.laminar.workspace.domain.WorkspaceRole;
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
 * <p>모두 워크스페이스 진입 후 호출 (X-Laminar-Workspace-Id 헤더 필수). 변경 작업은 OWNER (canWrite) 만 — owner 제거 차단.
 */
@RestController
@RequestMapping("/api/workspaces/current/members")
public class WorkspaceMemberController {

  private final WorkspaceMemberService memberService;

  public WorkspaceMemberController(WorkspaceMemberService memberService) {
    this.memberService = memberService;
  }

  @GetMapping
  public ResponseEntity<List<MemberResponse>> list() {
    return ResponseEntity.ok(
        memberService.listCurrentMembers().stream()
            .map(WorkspaceMemberController::toResponse)
            .toList());
  }

  @PatchMapping("/{userId}/role")
  public ResponseEntity<MemberResponse> updateRole(
      Authentication authentication,
      @PathVariable UUID userId,
      @Valid @RequestBody UpdateRoleRequest request) {
    if (!WorkspaceContextHolder.require().canWrite()) {
      return ResponseEntity.status(403).build();
    }
    LaminarPrincipal principal = requirePrincipal(authentication);
    WorkspaceMemberEntity updated =
        memberService.updateRole(userId, request.role(), principal.userId());
    return ResponseEntity.ok(toResponseFromEntity(updated));
  }

  @DeleteMapping("/{userId}")
  public ResponseEntity<Void> remove(Authentication authentication, @PathVariable UUID userId) {
    if (!WorkspaceContextHolder.require().canWrite()) {
      return ResponseEntity.status(403).build();
    }
    LaminarPrincipal principal = requirePrincipal(authentication);
    memberService.removeMember(userId, principal.userId());
    return ResponseEntity.noContent().build();
  }

  public record MemberResponse(
      UUID workspaceId,
      UUID userId,
      String email,
      String displayName,
      WorkspaceRole role,
      OffsetDateTime joinedAt) {}

  public record UpdateRoleRequest(@NotNull WorkspaceRole role) {}

  private static MemberResponse toResponse(WorkspaceMemberService.MemberView v) {
    return new MemberResponse(
        v.workspaceId(), v.userId(), v.email(), v.displayName(), v.role(), v.joinedAt());
  }

  private static MemberResponse toResponseFromEntity(WorkspaceMemberEntity m) {
    return new MemberResponse(
        m.getId().getWorkspaceId(),
        m.getId().getUserId(),
        null,
        null,
        m.getRole(),
        m.getJoinedAt());
  }

  private LaminarPrincipal requirePrincipal(Authentication authentication) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof LaminarPrincipal principal)) {
      throw new IllegalStateException("authentication required");
    }
    return principal;
  }
}
