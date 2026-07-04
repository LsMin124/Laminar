package com.laminar.whiteboard.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.whiteboard.domain.WhiteboardEdgeEntity;
import com.laminar.whiteboard.domain.WhiteboardNodeEntity;
import com.laminar.whiteboard.repository.WhiteboardEdgeRepository;
import com.laminar.whiteboard.repository.WhiteboardNodeRepository;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 화이트보드 엣지(노드 사이 화살표) CRUD. DAG의 시간 강제·비순환이 없어 사이클을 허용한다. 검증: self-loop 금지 + 두 노드가 같은 탭. */
@Service
public class WhiteboardEdgeService {

  private final WhiteboardEdgeRepository edgeRepo;
  private final WhiteboardNodeRepository nodeRepo;

  public WhiteboardEdgeService(
      WhiteboardEdgeRepository edgeRepo, WhiteboardNodeRepository nodeRepo) {
    this.edgeRepo = edgeRepo;
    this.nodeRepo = nodeRepo;
  }

  @Transactional
  public WhiteboardEdgeEntity create(
      UUID fromNodeId,
      UUID toNodeId,
      String relationKind,
      String label,
      Map<String, Object> attrs) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("whiteboard edges");
    if (Objects.equals(fromNodeId, toNodeId)) {
      throw new IllegalArgumentException("from_node_id == to_node_id is not allowed");
    }
    WhiteboardNodeEntity from = nodeRepo.findOwnedActiveOrThrow(fromNodeId, ctx, "from node");
    WhiteboardNodeEntity to = nodeRepo.findOwnedActiveOrThrow(toNodeId, ctx, "to node");
    UUID tabId = from.getTabId();
    if (tabId == null || !Objects.equals(tabId, to.getTabId())) {
      throw new IllegalArgumentException("from/to nodes must share a tab");
    }
    WhiteboardEdgeEntity edge = new WhiteboardEdgeEntity();
    edge.setSubjectId(ctx.subjectId());
    edge.setUserId(ctx.userId());
    edge.setCreatedBy(ctx.userId());
    edge.setTabId(tabId);
    edge.setFromNodeId(fromNodeId);
    edge.setToNodeId(toNodeId);
    edge.setRelationKind(relationKind == null || relationKind.isBlank() ? "default" : relationKind);
    edge.setLabel(label == null || label.isBlank() ? null : label);
    edge.setAttrs(attrs == null ? new HashMap<>() : attrs);
    return edgeRepo.save(edge);
  }

  @Transactional(readOnly = true)
  public List<WhiteboardEdgeEntity> listByTab(UUID tabId) {
    SubjectContextHolder.requirePersonal();
    return edgeRepo.findByTabIdAndDeletedAtIsNull(tabId);
  }

  /** 엣지 라벨 수정 — label이 곧 관계(별도 분류 없음). null/빈 값은 라벨 제거. */
  @Transactional
  public WhiteboardEdgeEntity update(UUID edgeId, String label) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("whiteboard edges");
    WhiteboardEdgeEntity edge = edgeRepo.findOwnedActiveOrThrow(edgeId, ctx, "whiteboard edge");
    edge.setLabel(label == null || label.isBlank() ? null : label);
    return edgeRepo.save(edge);
  }

  @Transactional
  public void softDelete(UUID edgeId) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("whiteboard edges");
    edgeRepo
        .findOwnedActive(edgeId, ctx)
        .ifPresent(
            edge -> {
              edge.setDeletedAt(OffsetDateTime.now());
              edgeRepo.save(edge);
            });
  }
}
