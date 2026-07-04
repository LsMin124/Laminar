package com.laminar.whiteboard.presentation;

import com.laminar.whiteboard.application.WhiteboardGraphService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/tabs/{tabId}/whiteboard — 화이트보드 전체 그래프(노드 + 엣지) 1회 fetch. */
@RestController
@RequestMapping("/api/tabs")
public class WhiteboardGraphController {

  private final WhiteboardGraphService graphService;

  public WhiteboardGraphController(WhiteboardGraphService graphService) {
    this.graphService = graphService;
  }

  @GetMapping("/{tabId}/whiteboard")
  public ResponseEntity<WhiteboardGraphResponse> graph(@PathVariable UUID tabId) {
    WhiteboardGraphService.WhiteboardGraph g = graphService.getGraph(tabId);
    return ResponseEntity.ok(
        new WhiteboardGraphResponse(
            g.tabId(),
            g.nodes().stream().map(WhiteboardNodeController::toResponse).toList(),
            g.edges().stream().map(WhiteboardEdgeController::toResponse).toList()));
  }

  public record WhiteboardGraphResponse(
      UUID tabId,
      List<WhiteboardNodeDtos.NodeResponse> nodes,
      List<WhiteboardEdgeDtos.EdgeResponse> edges) {}
}
