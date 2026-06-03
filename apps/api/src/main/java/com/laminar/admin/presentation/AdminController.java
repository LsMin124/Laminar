package com.laminar.admin.presentation;

import com.laminar.admin.application.AdminSubjectService;
import com.laminar.tab.domain.TabEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/admin/** — 운영 콘솔. OWNER 강제 + audit 자동.
 *
 * <p>엔드포인트: GET /tabs (cross-user 메타 목록) GET /tabs/{tabId}/cards (메타만, body 제외) POST
 * /cards/{cardId}/reveal-body (escape hatch — reason 필수, audit 강한 기록)
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final AdminSubjectService adminService;

  public AdminController(AdminSubjectService adminService) {
    this.adminService = adminService;
  }

  @GetMapping("/tabs")
  public ResponseEntity<List<TabSummaryResponse>> listAllTabs() {
    return ResponseEntity.ok(
        adminService.listAllTabs().stream().map(AdminController::toTabSummary).toList());
  }

  @GetMapping("/tabs/{tabId}/cards")
  public ResponseEntity<List<Map<String, Object>>> listCardMetadata(@PathVariable UUID tabId) {
    return ResponseEntity.ok(adminService.listCardMetadataByTab(tabId));
  }

  @PostMapping("/cards/{cardId}/reveal-body")
  public ResponseEntity<CardBodyRevealResponse> revealCardBody(
      @PathVariable UUID cardId, @Valid @RequestBody RevealBodyRequest request) {
    return adminService
        .revealCardBody(cardId, request.reason())
        .map(
            card ->
                new CardBodyRevealResponse(
                    card.getId(), card.getUserId(), card.getTitle(), card.getBodyMd()))
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  public record RevealBodyRequest(@NotBlank @Size(min = 10, max = 1000) String reason) {}

  public record TabSummaryResponse(
      UUID id, UUID subjectId, UUID userId, String name, String slug, int priority) {}

  public record CardBodyRevealResponse(UUID cardId, UUID userId, String title, String bodyMd) {}

  private static TabSummaryResponse toTabSummary(TabEntity b) {
    return new TabSummaryResponse(
        b.getId(), b.getSubjectId(), b.getUserId(), b.getName(), b.getSlug(), b.getPriority());
  }
}
