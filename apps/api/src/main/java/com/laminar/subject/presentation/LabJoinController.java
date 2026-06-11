package com.laminar.subject.presentation;

import com.laminar.security.LaminarPrincipal;
import com.laminar.subject.application.LabJoinService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * LAB 가입 흐름 HTTP 표면 (LAB재설계 §2).
 *
 * <p>관리자 측(/api/subjects/current/lab/**)은 lab PERSONAL 컨텍스트 + ADMIN+(서비스 가드). 신청자
 * 측(/api/labs/join)은 비멤버라 <b>subject 헤더 없이</b>(SYSTEM scope) 호출해야 한다 — 헤더가 있으면 그 주제의 컨텍스트 필터가 코드
 * 조회를 가로막는다(FE는 skipSubjectHeader로 호출).
 */
@RestController
public class LabJoinController {

  private final LabJoinService labJoinService;

  public LabJoinController(LabJoinService labJoinService) {
    this.labJoinService = labJoinService;
  }

  /** 초대코드 발급/회전 — 기존 코드는 즉시 무효. */
  @PostMapping("/api/subjects/current/lab/invite-code")
  public ResponseEntity<InviteCodeResponse> rotateInviteCode(Authentication authentication) {
    LaminarPrincipal principal = requirePrincipal(authentication);
    var code = labJoinService.rotateInviteCode(principal.userId());
    return ResponseEntity.ok(new InviteCodeResponse(code.getCode(), code.getCreatedAt()));
  }

  /** 현재 활성 초대코드 — 미발급이면 code=null. */
  @GetMapping("/api/subjects/current/lab/invite-code")
  public ResponseEntity<InviteCodeResponse> currentInviteCode() {
    return ResponseEntity.ok(
        labJoinService
            .currentInviteCode()
            .map(c -> new InviteCodeResponse(c.getCode(), c.getCreatedAt()))
            .orElse(new InviteCodeResponse(null, null)));
  }

  @GetMapping("/api/subjects/current/lab/join-requests")
  public ResponseEntity<List<JoinRequestResponse>> listJoinRequests() {
    return ResponseEntity.ok(
        labJoinService.listPending().stream()
            .map(
                v ->
                    new JoinRequestResponse(
                        v.id(), v.userId(), v.email(), v.displayName(), v.requestedAt()))
            .toList());
  }

  @PostMapping("/api/subjects/current/lab/join-requests/{requestId}/approve")
  public ResponseEntity<Void> approve(Authentication authentication, @PathVariable UUID requestId) {
    labJoinService.approve(requestId, requirePrincipal(authentication).userId());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/subjects/current/lab/join-requests/{requestId}/reject")
  public ResponseEntity<Void> reject(Authentication authentication, @PathVariable UUID requestId) {
    labJoinService.reject(requestId, requirePrincipal(authentication).userId());
    return ResponseEntity.noContent().build();
  }

  /** 초대코드로 가입 신청 — 승인 대기(pending) 생성. 멱등(기존 pending 반환). */
  @PostMapping("/api/labs/join")
  public ResponseEntity<JoinResponse> join(
      Authentication authentication, @Valid @RequestBody JoinRequest request) {
    LaminarPrincipal principal = requirePrincipal(authentication);
    LabJoinService.JoinOutcome outcome = labJoinService.join(request.code(), principal.userId());
    return ResponseEntity.ok(
        new JoinResponse(outcome.labId(), outcome.labName(), outcome.status().name()));
  }

  private LaminarPrincipal requirePrincipal(Authentication authentication) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof LaminarPrincipal principal)) {
      throw new IllegalStateException("authentication required");
    }
    return principal;
  }

  public record InviteCodeResponse(String code, OffsetDateTime createdAt) {}

  public record JoinRequestResponse(
      UUID id, UUID userId, String email, String displayName, OffsetDateTime requestedAt) {}

  public record JoinRequest(@NotBlank String code) {}

  public record JoinResponse(UUID labId, String labName, String status) {}
}
