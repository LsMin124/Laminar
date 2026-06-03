package com.laminar.admin.presentation;

import com.laminar.admin.application.AdminWorkspaceService;
import com.laminar.board.domain.BoardEntity;
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
 * <p>엔드포인트: GET /boards (cross-user 메타 목록) GET /boards/{boardId}/cards (메타만, body 제외) POST
 * /cards/{cardId}/reveal-body (escape hatch — reason 필수, audit 강한 기록)
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final AdminWorkspaceService adminService;

  public AdminController(AdminWorkspaceService adminService) {
    this.adminService = adminService;
  }

  @GetMapping("/boards")
  public ResponseEntity<List<BoardSummaryResponse>> listAllBoards() {
    return ResponseEntity.ok(
        adminService.listAllBoards().stream().map(AdminController::toBoardSummary).toList());
  }

  @GetMapping("/boards/{boardId}/cards")
  public ResponseEntity<List<Map<String, Object>>> listCardMetadata(@PathVariable UUID boardId) {
    return ResponseEntity.ok(adminService.listCardMetadataByBoard(boardId));
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

  public record BoardSummaryResponse(
      UUID id, UUID workspaceId, UUID userId, String name, String slug, int priority) {}

  public record CardBodyRevealResponse(UUID cardId, UUID userId, String title, String bodyMd) {}

  private static BoardSummaryResponse toBoardSummary(BoardEntity b) {
    return new BoardSummaryResponse(
        b.getId(), b.getWorkspaceId(), b.getUserId(), b.getName(), b.getSlug(), b.getPriority());
  }
}
