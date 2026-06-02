package com.laminar.web.perpetual;

import com.laminar.perpetual.PerpetualNoteEntity;
import com.laminar.perpetual.PerpetualNoteService;
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

@RestController
@RequestMapping("/api")
public class PerpetualNoteController {

  private final PerpetualNoteService service;

  public PerpetualNoteController(PerpetualNoteService service) {
    this.service = service;
  }

  @PostMapping("/perpetual-notes")
  public ResponseEntity<PerpetualNoteDtos.PerpetualNoteResponse> create(
      @Valid @RequestBody PerpetualNoteDtos.CreateRequest request) {
    PerpetualNoteEntity note =
        service.create(
            request.boardId(),
            request.tabId(),
            request.parentPerpetualId(),
            request.title(),
            request.bodyMd(),
            request.attrs());
    return ResponseEntity.ok(toResponse(note));
  }

  @GetMapping("/boards/{boardId}/perpetual-notes")
  public ResponseEntity<List<PerpetualNoteDtos.PerpetualNoteResponse>> listByBoard(
      @PathVariable UUID boardId) {
    return ResponseEntity.ok(service.listByBoard(boardId).stream().map(this::toResponse).toList());
  }

  @GetMapping("/tabs/{tabId}/perpetual-notes")
  public ResponseEntity<List<PerpetualNoteDtos.PerpetualNoteResponse>> listByTab(
      @PathVariable UUID tabId) {
    return ResponseEntity.ok(service.listByTab(tabId).stream().map(this::toResponse).toList());
  }

  @GetMapping("/tabs/{tabId}/perpetual-notes/roots")
  public ResponseEntity<List<PerpetualNoteDtos.PerpetualNoteResponse>> listRootsByTab(
      @PathVariable UUID tabId) {
    return ResponseEntity.ok(service.listRootsByTab(tabId).stream().map(this::toResponse).toList());
  }

  @GetMapping("/perpetual-notes/{noteId}/children")
  public ResponseEntity<List<PerpetualNoteDtos.PerpetualNoteResponse>> listChildren(
      @PathVariable UUID noteId) {
    return ResponseEntity.ok(service.listChildren(noteId).stream().map(this::toResponse).toList());
  }

  @GetMapping("/perpetual-notes/{noteId}")
  public ResponseEntity<PerpetualNoteDtos.PerpetualNoteResponse> get(@PathVariable UUID noteId) {
    return service
        .findById(noteId)
        .map(this::toResponse)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PatchMapping("/perpetual-notes/{noteId}")
  public ResponseEntity<PerpetualNoteDtos.PerpetualNoteResponse> update(
      @PathVariable UUID noteId, @Valid @RequestBody PerpetualNoteDtos.UpdateRequest request) {
    PerpetualNoteEntity updated =
        service.update(
            noteId,
            request.title(),
            request.bodyMd(),
            request.parentPerpetualId(),
            request.attrs());
    return ResponseEntity.ok(toResponse(updated));
  }

  @PatchMapping("/perpetual-notes/reorder")
  public ResponseEntity<List<PerpetualNoteDtos.PerpetualNoteResponse>> reorder(
      @Valid @RequestBody PerpetualNoteDtos.ReorderRequest request) {
    return ResponseEntity.ok(
        service.reorder(request.tabId(), request.orderedIds()).stream()
            .map(this::toResponse)
            .toList());
  }

  @DeleteMapping("/perpetual-notes/{noteId}")
  public ResponseEntity<Void> delete(@PathVariable UUID noteId) {
    service.softDelete(noteId);
    return ResponseEntity.noContent().build();
  }

  private PerpetualNoteDtos.PerpetualNoteResponse toResponse(PerpetualNoteEntity n) {
    return new PerpetualNoteDtos.PerpetualNoteResponse(
        n.getId(),
        n.getWorkspaceId(),
        n.getUserId(),
        n.getBoardId(),
        n.getTabId(),
        n.getParentPerpetualId(),
        n.getTitle(),
        n.getBodyMd(),
        n.getPriority(),
        n.getAttrs(),
        n.getCreatedAt(),
        n.getUpdatedAt());
  }
}
