package com.laminar.whiteboard.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.whiteboard.domain.WhiteboardNodeEntity;
import com.laminar.whiteboard.domain.WhiteboardNodeKind;
import com.laminar.whiteboard.repository.WhiteboardNodeRepository;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 화이트보드 노드 CRUD — Personal-First 격리. 자유 x,y 이동·리사이즈가 곧 update다(카드의 DAG 시간 강제·연쇄 이동이 여기엔 없다). */
@Service
public class WhiteboardNodeService {

  private final WhiteboardNodeRepository nodeRepo;

  public WhiteboardNodeService(WhiteboardNodeRepository nodeRepo) {
    this.nodeRepo = nodeRepo;
  }

  @Transactional
  public WhiteboardNodeEntity create(CreateInput input) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("whiteboard nodes");
    WhiteboardNodeEntity node = new WhiteboardNodeEntity();
    node.setSubjectId(ctx.subjectId());
    node.setUserId(ctx.userId());
    node.setCreatedBy(ctx.userId());
    node.setTabId(input.tabId());
    node.setKind(input.kind());
    node.setX(input.x());
    node.setY(input.y());
    node.setWidth(input.width());
    node.setHeight(input.height());
    node.setText(input.text());
    node.setBodyMd(input.bodyMd());
    node.setAttrs(input.attrs() == null ? new HashMap<>() : input.attrs());
    return nodeRepo.save(node);
  }

  @Transactional(readOnly = true)
  public List<WhiteboardNodeEntity> listByTab(UUID tabId) {
    SubjectContextHolder.requirePersonal();
    return nodeRepo.findByTabIdAndDeletedAtIsNull(tabId);
  }

  @Transactional(readOnly = true)
  public Optional<WhiteboardNodeEntity> findById(UUID nodeId) {
    SubjectContext ctx = SubjectContextHolder.requirePersonal();
    return nodeRepo.findOwnedActive(nodeId, ctx);
  }

  /** 이동·리사이즈·본문 편집 — PATCH 규약(null=무변경). x/y도 개별 null 가능(이동만·리사이즈만 부분 patch). */
  @Transactional
  public WhiteboardNodeEntity update(UUID nodeId, UpdateInput input) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("whiteboard nodes");
    WhiteboardNodeEntity node = nodeRepo.findOwnedActiveOrThrow(nodeId, ctx, "whiteboard node");
    if (input.x() != null) node.setX(input.x());
    if (input.y() != null) node.setY(input.y());
    if (input.width() != null) node.setWidth(input.width());
    if (input.height() != null) node.setHeight(input.height());
    if (input.text() != null) node.setText(input.text());
    if (input.bodyMd() != null) node.setBodyMd(input.bodyMd());
    if (input.attrs() != null) node.setAttrs(input.attrs());
    return nodeRepo.save(node);
  }

  @Transactional
  public void softDelete(UUID nodeId) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("whiteboard nodes");
    nodeRepo
        .findOwnedActive(nodeId, ctx)
        .ifPresent(
            node -> {
              node.setDeletedAt(OffsetDateTime.now());
              nodeRepo.save(node);
            });
  }

  public record CreateInput(
      UUID tabId,
      WhiteboardNodeKind kind,
      double x,
      double y,
      Double width,
      Double height,
      String text,
      String bodyMd,
      Map<String, Object> attrs) {}

  public record UpdateInput(
      Double x,
      Double y,
      Double width,
      Double height,
      String text,
      String bodyMd,
      Map<String, Object> attrs) {}
}
