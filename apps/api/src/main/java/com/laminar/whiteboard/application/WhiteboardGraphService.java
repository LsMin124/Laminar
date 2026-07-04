package com.laminar.whiteboard.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.whiteboard.domain.WhiteboardEdgeEntity;
import com.laminar.whiteboard.domain.WhiteboardNodeEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 화이트보드 그래프 뷰 — 노드 + 엣지 1회 fetch (TabGraphService 미러). 읽기 전용이라 쓰기 가드 비대상. 격리는 노드/엣지 서비스의
 * Personal-First 필터에 위임한다.
 */
@Service
public class WhiteboardGraphService {

  private final WhiteboardNodeService nodeService;
  private final WhiteboardEdgeService edgeService;

  public WhiteboardGraphService(
      WhiteboardNodeService nodeService, WhiteboardEdgeService edgeService) {
    this.nodeService = nodeService;
    this.edgeService = edgeService;
  }

  @Transactional(readOnly = true)
  public WhiteboardGraph getGraph(UUID tabId) {
    SubjectContext ctx = SubjectContextHolder.require();
    if (ctx.scope() != SubjectContext.Scope.PERSONAL) {
      throw new IllegalStateException("PERSONAL scope required for whiteboard graph");
    }
    List<WhiteboardNodeEntity> nodes = nodeService.listByTab(tabId);
    List<WhiteboardEdgeEntity> edges = edgeService.listByTab(tabId);
    return new WhiteboardGraph(tabId, nodes, edges);
  }

  public record WhiteboardGraph(
      UUID tabId, List<WhiteboardNodeEntity> nodes, List<WhiteboardEdgeEntity> edges) {}
}
