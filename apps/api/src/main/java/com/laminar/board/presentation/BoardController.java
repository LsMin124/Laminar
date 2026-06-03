package com.laminar.board.presentation;

import com.laminar.board.application.BoardService;
import com.laminar.board.domain.BoardEntity;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/boards — 보드 CRUD. PERSONAL scope 진입 후 호출 (X-Laminar-Workspace-Id 헤더 필수). */
@RestController
@RequestMapping("/api/boards")
public class BoardController {

  private final BoardService boardService;

  public BoardController(BoardService boardService) {
    this.boardService = boardService;
  }

  @PostMapping
  public ResponseEntity<BoardDtos.BoardResponse> create(
      @Valid @RequestBody BoardDtos.CreateRequest request) {
    BoardEntity board =
        boardService.create(
            request.name(),
            request.slug(),
            request.defaultView(),
            request.iconName(),
            request.iconColor(),
            request.settings());
    return ResponseEntity.ok(toResponse(board));
  }

  @GetMapping
  public ResponseEntity<List<BoardDtos.BoardResponse>> list() {
    List<BoardDtos.BoardResponse> response =
        boardService.listActive().stream().map(this::toResponse).toList();
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{boardId}")
  public ResponseEntity<BoardDtos.BoardResponse> get(@PathVariable UUID boardId) {
    return boardService
        .findById(boardId)
        .map(this::toResponse)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PatchMapping("/{boardId}")
  public ResponseEntity<BoardDtos.BoardResponse> update(
      @PathVariable UUID boardId, @Valid @RequestBody BoardDtos.UpdateRequest request) {
    BoardEntity updated =
        boardService.update(
            boardId,
            request.name(),
            request.defaultView(),
            request.iconName(),
            request.iconColor(),
            request.settings());
    return ResponseEntity.ok(toResponse(updated));
  }

  @DeleteMapping("/{boardId}")
  public ResponseEntity<Void> delete(@PathVariable UUID boardId) {
    boardService.softDelete(boardId);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/reorder")
  public ResponseEntity<List<BoardDtos.BoardResponse>> reorder(
      @Valid @RequestBody BoardDtos.ReorderRequest request) {
    return ResponseEntity.ok(
        boardService.reorder(request.orderedIds()).stream().map(this::toResponse).toList());
  }

  private BoardDtos.BoardResponse toResponse(BoardEntity board) {
    return new BoardDtos.BoardResponse(
        board.getId(),
        board.getWorkspaceId(),
        board.getUserId(),
        board.getName(),
        board.getSlug(),
        board.getDefaultView(),
        board.getIconName(),
        board.getIconColor(),
        board.getSettings(),
        board.getPriority(),
        board.getCreatedAt(),
        board.getUpdatedAt());
  }
}
