package com.laminar.whiteboard.presentation;

import com.laminar.whiteboard.application.WhiteboardNodeService;
import com.laminar.whiteboard.domain.WhiteboardNodeEntity;
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

/** /api/whiteboard-nodes — 화이트보드 노드 CRUD. PERSONAL scope 진입 후 호출. */
@RestController
@RequestMapping("/api")
public class WhiteboardNodeController {

  private final WhiteboardNodeService service;

  public WhiteboardNodeController(WhiteboardNodeService service) {
    this.service = service;
  }

  @PostMapping("/whiteboard-nodes")
  public ResponseEntity<WhiteboardNodeDtos.NodeResponse> create(
      @Valid @RequestBody WhiteboardNodeDtos.CreateRequest request) {
    WhiteboardNodeEntity created =
        service.create(
            new WhiteboardNodeService.CreateInput(
                request.tabId(),
                request.kind(),
                request.x(),
                request.y(),
                request.width(),
                request.height(),
                request.text(),
                request.bodyMd(),
                request.attrs()));
    return ResponseEntity.ok(toResponse(created));
  }

  @PatchMapping("/whiteboard-nodes/{nodeId}")
  public ResponseEntity<WhiteboardNodeDtos.NodeResponse> update(
      @PathVariable UUID nodeId, @Valid @RequestBody WhiteboardNodeDtos.UpdateRequest request) {
    WhiteboardNodeEntity updated =
        service.update(
            nodeId,
            new WhiteboardNodeService.UpdateInput(
                request.x(),
                request.y(),
                request.width(),
                request.height(),
                request.text(),
                request.bodyMd(),
                request.attrs()));
    return ResponseEntity.ok(toResponse(updated));
  }

  @DeleteMapping("/whiteboard-nodes/{nodeId}")
  public ResponseEntity<Void> delete(@PathVariable UUID nodeId) {
    service.softDelete(nodeId);
    return ResponseEntity.noContent().build();
  }

  /** WB-C undo — soft-delete 복구(같은 id 유지). */
  @PostMapping("/whiteboard-nodes/{nodeId}/restore")
  public ResponseEntity<WhiteboardNodeDtos.NodeResponse> restore(@PathVariable UUID nodeId) {
    return ResponseEntity.ok(toResponse(service.restore(nodeId)));
  }

  /** 그래프 BFF도 재사용하는 매핑 정본(중복 방지). */
  static WhiteboardNodeDtos.NodeResponse toResponse(WhiteboardNodeEntity n) {
    return new WhiteboardNodeDtos.NodeResponse(
        n.getId(),
        n.getSubjectId(),
        n.getUserId(),
        n.getTabId(),
        n.getKind(),
        n.getX(),
        n.getY(),
        n.getWidth(),
        n.getHeight(),
        n.getText(),
        n.getBodyMd(),
        n.getAttrs(),
        n.getCreatedAt(),
        n.getUpdatedAt());
  }
}
