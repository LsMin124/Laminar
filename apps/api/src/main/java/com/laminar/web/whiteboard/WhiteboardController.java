package com.laminar.web.whiteboard;

import com.laminar.whiteboard.WhiteboardEdgeEntity;
import com.laminar.whiteboard.WhiteboardNodeEntity;
import com.laminar.whiteboard.WhiteboardService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 독립 화이트보드 API — board별 노드·엣지. 타임라인/캘린더 데이터와 무관.
 */
@RestController
@RequestMapping("/api")
public class WhiteboardController {

    private final WhiteboardService service;

    public WhiteboardController(WhiteboardService service) {
        this.service = service;
    }

    @GetMapping("/boards/{boardId}/whiteboard")
    public ResponseEntity<WhiteboardDtos.WhiteboardResponse> get(@PathVariable UUID boardId) {
        List<WhiteboardDtos.NodeResponse> nodes =
                service.listNodes(boardId).stream().map(this::toNode).toList();
        List<WhiteboardDtos.EdgeResponse> edges =
                service.listEdges(boardId).stream().map(this::toEdge).toList();
        return ResponseEntity.ok(new WhiteboardDtos.WhiteboardResponse(boardId, nodes, edges));
    }

    @PostMapping("/boards/{boardId}/whiteboard/nodes")
    public ResponseEntity<WhiteboardDtos.NodeResponse> createNode(
            @PathVariable UUID boardId,
            @Valid @RequestBody WhiteboardDtos.CreateNodeRequest request) {
        WhiteboardNodeEntity node = service.createNode(
                boardId, request.text(), request.x(), request.y(),
                request.width(), request.height(), request.color());
        return ResponseEntity.ok(toNode(node));
    }

    @PatchMapping("/whiteboard/nodes/{nodeId}")
    public ResponseEntity<WhiteboardDtos.NodeResponse> updateNode(
            @PathVariable UUID nodeId,
            @Valid @RequestBody WhiteboardDtos.UpdateNodeRequest request) {
        WhiteboardNodeEntity node = service.updateNode(
                nodeId, request.text(), request.x(), request.y(),
                request.width(), request.height(), request.color());
        return ResponseEntity.ok(toNode(node));
    }

    @DeleteMapping("/whiteboard/nodes/{nodeId}")
    public ResponseEntity<Void> deleteNode(@PathVariable UUID nodeId) {
        service.deleteNode(nodeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/boards/{boardId}/whiteboard/edges")
    public ResponseEntity<WhiteboardDtos.EdgeResponse> createEdge(
            @PathVariable UUID boardId,
            @Valid @RequestBody WhiteboardDtos.CreateEdgeRequest request) {
        WhiteboardEdgeEntity edge = service.createEdge(
                boardId, request.fromNodeId(), request.toNodeId(), request.label());
        return ResponseEntity.ok(toEdge(edge));
    }

    @DeleteMapping("/whiteboard/edges/{edgeId}")
    public ResponseEntity<Void> deleteEdge(@PathVariable UUID edgeId) {
        service.deleteEdge(edgeId);
        return ResponseEntity.noContent().build();
    }

    private WhiteboardDtos.NodeResponse toNode(WhiteboardNodeEntity n) {
        return new WhiteboardDtos.NodeResponse(
                n.getId(), n.getWorkspaceId(), n.getUserId(), n.getBoardId(),
                n.getText(), n.getX(), n.getY(), n.getWidth(), n.getHeight(), n.getColor(),
                n.getCreatedAt(), n.getUpdatedAt());
    }

    private WhiteboardDtos.EdgeResponse toEdge(WhiteboardEdgeEntity e) {
        return new WhiteboardDtos.EdgeResponse(
                e.getId(), e.getWorkspaceId(), e.getUserId(), e.getBoardId(),
                e.getFromNodeId(), e.getToNodeId(), e.getLabel(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
