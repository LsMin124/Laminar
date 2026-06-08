package com.laminar.subject.presentation;

import com.laminar.security.LaminarPrincipal;
import com.laminar.subject.application.SubjectService;
import com.laminar.subject.domain.SubjectEntity;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/subjects — 워크스페이스 CRUD.
 *
 * <p>GET (목록) / POST는 워크스페이스 진입 전 (SYSTEM scope, 헤더 불필요) 호출 가능 — 가입 직후 발견용. /current 시리즈는
 * X-Laminar-Subject-Id 헤더로 PERSONAL scope 진입 후 호출.
 */
@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

  private final SubjectService subjectService;

  public SubjectController(SubjectService subjectService) {
    this.subjectService = subjectService;
  }

  @PostMapping
  public ResponseEntity<SubjectDtos.SubjectResponse> create(
      Authentication authentication, @Valid @RequestBody SubjectDtos.CreateRequest request) {
    LaminarPrincipal principal = requirePrincipal(authentication);
    SubjectEntity subject =
        subjectService.create(
            principal.userId(), request.name(), request.slug(), request.defaultTimezone());
    return ResponseEntity.ok(toResponse(subject));
  }

  @GetMapping
  public ResponseEntity<List<SubjectDtos.SubjectResponse>> listMine(Authentication authentication) {
    LaminarPrincipal principal = requirePrincipal(authentication);
    return ResponseEntity.ok(
        subjectService.listForUser(principal.userId()).stream().map(this::toResponse).toList());
  }

  @GetMapping("/current")
  public ResponseEntity<SubjectDtos.SubjectResponse> current() {
    return ResponseEntity.ok(toResponse(subjectService.requireCurrent()));
  }

  @PatchMapping("/current")
  public ResponseEntity<SubjectDtos.SubjectResponse> updateCurrent(
      @Valid @RequestBody SubjectDtos.UpdateRequest request) {
    SubjectEntity updated =
        subjectService.updateCurrent(
            request.name(), request.defaultTimezone(), request.bodyMd(), request.settings());
    return ResponseEntity.ok(toResponse(updated));
  }

  @DeleteMapping("/current")
  public ResponseEntity<Void> deleteCurrent(Authentication authentication) {
    LaminarPrincipal principal = requirePrincipal(authentication);
    subjectService.deleteCurrent(principal.userId());
    return ResponseEntity.noContent().build();
  }

  private LaminarPrincipal requirePrincipal(Authentication authentication) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof LaminarPrincipal principal)) {
      throw new IllegalStateException("authentication required");
    }
    return principal;
  }

  private SubjectDtos.SubjectResponse toResponse(SubjectEntity ws) {
    return new SubjectDtos.SubjectResponse(
        ws.getId(),
        ws.getName(),
        ws.getSlug(),
        ws.getOwnerUserId(),
        ws.getDefaultTimezone(),
        ws.getBodyMd(),
        ws.getSettings(),
        ws.getCreatedAt(),
        ws.getUpdatedAt());
  }
}
