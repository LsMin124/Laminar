package com.laminar.whiteboard.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.error.NotFoundException;
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

  /**
   * WB-D — 엣지 끝점 재연결(같은 id·라벨 유지). from/to 중 null은 무변경. 검증은 create와 동일: self-loop 금지 + 두 노드가 엣지의 탭과
   * 일치. 동일 연결이 이미 있으면 활성 유니크 제약이 막는다.
   */
  @Transactional
  public WhiteboardEdgeEntity reconnect(UUID edgeId, UUID fromNodeId, UUID toNodeId) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("whiteboard edges");
    WhiteboardEdgeEntity edge = edgeRepo.findOwnedActiveOrThrow(edgeId, ctx, "whiteboard edge");
    UUID nextFrom = fromNodeId != null ? fromNodeId : edge.getFromNodeId();
    UUID nextTo = toNodeId != null ? toNodeId : edge.getToNodeId();
    if (Objects.equals(nextFrom, nextTo)) {
      throw new IllegalArgumentException("from_node_id == to_node_id is not allowed");
    }
    WhiteboardNodeEntity from = nodeRepo.findOwnedActiveOrThrow(nextFrom, ctx, "from node");
    WhiteboardNodeEntity to = nodeRepo.findOwnedActiveOrThrow(nextTo, ctx, "to node");
    if (!Objects.equals(from.getTabId(), edge.getTabId())
        || !Objects.equals(to.getTabId(), edge.getTabId())) {
      throw new IllegalArgumentException("from/to nodes must share the edge tab");
    }
    edge.setFromNodeId(nextFrom);
    edge.setToNodeId(nextTo);
    return edgeRepo.save(edge);
  }

  /**
   * WB-C undo — soft-delete 복구(같은 id 유지, 멱등). 삭제~복구 사이에 동일한 활성 엣지가 새로 생기면 활성 유니크 제약에 걸린다 — undo가 방금
   * 지운 엣지를 되살리는 흐름에선 실질적으로 발생하지 않아 별도 처리 없이 제약 위반을 그대로 흘린다.
   */
  @Transactional
  public WhiteboardEdgeEntity restore(UUID edgeId) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("whiteboard edges");
    WhiteboardEdgeEntity edge =
        edgeRepo
            .findById(edgeId)
            .filter(e -> ctx.ownsPersonal(e.getSubjectId(), e.getUserId()))
            .orElseThrow(() -> new NotFoundException("whiteboard edge not found"));
    edge.setDeletedAt(null);
    return edgeRepo.save(edge);
  }
}
