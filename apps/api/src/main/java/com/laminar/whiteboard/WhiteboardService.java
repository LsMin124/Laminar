package com.laminar.whiteboard;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 독립 화이트보드 — Personal-First. 타임라인/캘린더(카드·관계)와 무관한 자유 노드·엣지 캔버스.
 * 노드/엣지는 hard-delete (스크래치 공간). 엣지는 노드 FK CASCADE로 정리.
 */
@Service
public class WhiteboardService {

    private final WhiteboardNodeRepository nodeRepo;
    private final WhiteboardEdgeRepository edgeRepo;

    public WhiteboardService(WhiteboardNodeRepository nodeRepo, WhiteboardEdgeRepository edgeRepo) {
        this.nodeRepo = nodeRepo;
        this.edgeRepo = edgeRepo;
    }

    @Transactional(readOnly = true)
    public List<WhiteboardNodeEntity> listNodes(UUID boardId) {
        WorkspaceContextHolder.requirePersonal();
        return nodeRepo.findByBoardId(boardId);
    }

    @Transactional(readOnly = true)
    public List<WhiteboardEdgeEntity> listEdges(UUID boardId) {
        WorkspaceContextHolder.requirePersonal();
        return edgeRepo.findByBoardId(boardId);
    }

    @Transactional
    public WhiteboardNodeEntity createNode(
            UUID boardId, String text, double x, double y, Double width, Double height, String color) {
        WorkspaceContext ctx = requirePersonalWritable();
        WhiteboardNodeEntity n = new WhiteboardNodeEntity();
        n.setWorkspaceId(ctx.workspaceId());
        n.setUserId(ctx.userId());
        n.setCreatedBy(ctx.userId());
        n.setBoardId(boardId);
        n.setText(text == null ? "" : text);
        n.setX(x);
        n.setY(y);
        if (width != null) n.setWidth(width);
        if (height != null) n.setHeight(height);
        n.setColor(color);
        return nodeRepo.save(n);
    }

    @Transactional
    public WhiteboardNodeEntity updateNode(
            UUID nodeId, String text, Double x, Double y, Double width, Double height, String color) {
        WorkspaceContext ctx = requirePersonalWritable();
        WhiteboardNodeEntity n = nodeRepo.findById(nodeId)
                .filter(e -> ctx.ownsPersonal(e.getWorkspaceId(), e.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException("node not found: " + nodeId));
        if (text != null) n.setText(text);
        if (x != null) n.setX(x);
        if (y != null) n.setY(y);
        if (width != null) n.setWidth(width);
        if (height != null) n.setHeight(height);
        if (color != null) n.setColor(color);
        return nodeRepo.save(n);
    }

    @Transactional
    public void deleteNode(UUID nodeId) {
        WorkspaceContext ctx = requirePersonalWritable();
        nodeRepo.findById(nodeId)
                .filter(e -> ctx.ownsPersonal(e.getWorkspaceId(), e.getUserId()))
                .ifPresent(nodeRepo::delete); // 연결된 엣지는 FK ON DELETE CASCADE
    }

    @Transactional
    public WhiteboardEdgeEntity createEdge(UUID boardId, UUID fromNodeId, UUID toNodeId, String label) {
        WorkspaceContext ctx = requirePersonalWritable();
        if (fromNodeId.equals(toNodeId)) {
            throw new IllegalArgumentException("cannot connect a node to itself");
        }
        nodeRepo.findById(fromNodeId)
                .filter(e -> ctx.ownsPersonal(e.getWorkspaceId(), e.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException("from node not found: " + fromNodeId));
        nodeRepo.findById(toNodeId)
                .filter(e -> ctx.ownsPersonal(e.getWorkspaceId(), e.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException("to node not found: " + toNodeId));
        WhiteboardEdgeEntity edge = new WhiteboardEdgeEntity();
        edge.setWorkspaceId(ctx.workspaceId());
        edge.setUserId(ctx.userId());
        edge.setCreatedBy(ctx.userId());
        edge.setBoardId(boardId);
        edge.setFromNodeId(fromNodeId);
        edge.setToNodeId(toNodeId);
        edge.setLabel(label);
        return edgeRepo.save(edge);
    }

    @Transactional
    public void deleteEdge(UUID edgeId) {
        WorkspaceContext ctx = requirePersonalWritable();
        edgeRepo.findById(edgeId)
                .filter(e -> ctx.ownsPersonal(e.getWorkspaceId(), e.getUserId()))
                .ifPresent(edgeRepo::delete);
    }

    private WorkspaceContext requirePersonalWritable() {
        WorkspaceContext ctx = WorkspaceContextHolder.require();
        if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
            throw new IllegalStateException("PERSONAL scope required");
        }
        if (!ctx.canWrite()) {
            throw new IllegalStateException("VIEWER cannot mutate whiteboard");
        }
        return ctx;
    }
}
