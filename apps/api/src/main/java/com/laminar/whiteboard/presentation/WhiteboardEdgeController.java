package com.laminar.whiteboard.presentation;

import com.laminar.whiteboard.application.WhiteboardEdgeService;
import com.laminar.whiteboard.domain.WhiteboardEdgeEntity;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/whiteboard-edges — 노드 사이 화살표 CRUD. PERSONAL scope 진입 후 호출. */
@RestController
@RequestMapping("/api")
public class WhiteboardEdgeController {

  private final WhiteboardEdgeService service;

  public WhiteboardEdgeController(WhiteboardEdgeService service) {
    this.service = service;
  }

  @PostMapping("/whiteboard-edges")
  public ResponseEntity<WhiteboardEdgeDtos.EdgeResponse> create(
      @Valid @RequestBody WhiteboardEdgeDtos.CreateRequest request) {
    WhiteboardEdgeEntity created =
        service.create(
            request.fromNodeId(),
            request.toNodeId(),
            request.relationKind(),
            request.label(),
            request.attrs());
    return ResponseEntity.ok(toResponse(created));
  }

  @PatchMapping("/whiteboard-edges/{edgeId}")
  public ResponseEntity<WhiteboardEdgeDtos.EdgeResponse> update(
      @PathVariable UUID edgeId, @Valid @RequestBody WhiteboardEdgeDtos.UpdateRequest request) {
    return ResponseEntity.ok(toResponse(service.update(edgeId, request.label())));
  }

  @DeleteMapping("/whiteboard-edges/{edgeId}")
  public ResponseEntity<Void> delete(@PathVariable UUID edgeId) {
    service.softDelete(edgeId);
    return ResponseEntity.noContent().build();
  }

  /** 그래프 BFF도 재사용하는 매핑 정본(중복 방지). */
  static WhiteboardEdgeDtos.EdgeResponse toResponse(WhiteboardEdgeEntity e) {
    return new WhiteboardEdgeDtos.EdgeResponse(
        e.getId(),
        e.getSubjectId(),
        e.getUserId(),
        e.getTabId(),
        e.getFromNodeId(),
        e.getToNodeId(),
        e.getRelationKind(),
        e.getLabel(),
        e.getAttrs(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }
}
