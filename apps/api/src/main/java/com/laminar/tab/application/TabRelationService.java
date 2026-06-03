package com.laminar.tab.application;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.tab.domain.TabEntity;
import com.laminar.tab.domain.TabRelationEntity;
import com.laminar.tab.repository.TabRelationRepository;
import com.laminar.tab.repository.TabRepository;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탭 사이 화살표 — DAG 강제 (사이클 차단).
 *
 * <p>Spec §2.4.3: "DAG 강제는 서비스 레이어". 신규 엣지 추가 시 to → from 도달성 검사 후 사이클 형성 차단.
 */
@Service
public class TabRelationService {

  private final TabRelationRepository relationRepo;
  private final TabRepository tabRepo;

  public TabRelationService(TabRelationRepository relationRepo, TabRepository tabRepo) {
    this.relationRepo = relationRepo;
    this.tabRepo = tabRepo;
  }

  @Transactional
  public TabRelationEntity create(
      UUID fromTabId, UUID toTabId, String summary, String bodyMd, Map<String, Object> attrs) {
    WorkspaceContext ctx = requirePersonalWritable();
    if (Objects.equals(fromTabId, toTabId)) {
      throw new IllegalArgumentException("from_tab_id == to_tab_id is not allowed");
    }
    TabEntity from =
        tabRepo
            .findById(fromTabId)
            .filter(t -> t.getDeletedAt() == null)
            .filter(t -> ctx.ownsPersonal(t.getWorkspaceId(), t.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("from tab not found"));
    TabEntity to =
        tabRepo
            .findById(toTabId)
            .filter(t -> t.getDeletedAt() == null)
            .filter(t -> ctx.ownsPersonal(t.getWorkspaceId(), t.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("to tab not found"));
    if (!Objects.equals(from.getBoardId(), to.getBoardId())) {
      throw new IllegalArgumentException("from/to tabs must belong to the same board");
    }

    if (reachable(to.getId(), from.getId(), from.getBoardId())) {
      throw new IllegalArgumentException("cycle detected: relation would create a cycle");
    }

    TabRelationEntity relation = new TabRelationEntity();
    relation.setWorkspaceId(ctx.workspaceId());
    relation.setUserId(ctx.userId());
    relation.setCreatedBy(ctx.userId());
    relation.setBoardId(from.getBoardId());
    relation.setFromTabId(fromTabId);
    relation.setToTabId(toTabId);
    relation.setSummary(summary);
    relation.setBodyMd(bodyMd);
    relation.setAttrs(attrs == null ? new HashMap<>() : attrs);
    return relationRepo.save(relation);
  }

  @Transactional(readOnly = true)
  public List<TabRelationEntity> listByBoard(UUID boardId) {
    WorkspaceContextHolder.requirePersonal();
    return relationRepo.findByBoardIdAndDeletedAtIsNull(boardId);
  }

  @Transactional
  public void softDelete(UUID relationId) {
    WorkspaceContext ctx = requirePersonalWritable();
    relationRepo
        .findById(relationId)
        .filter(r -> r.getDeletedAt() == null)
        .filter(r -> ctx.ownsPersonal(r.getWorkspaceId(), r.getUserId()))
        .ifPresent(
            r -> {
              r.setDeletedAt(OffsetDateTime.now());
              relationRepo.save(r);
            });
  }

  /** 보드 내 활성 엣지를 따라 source → target 도달 가능한지 BFS. */
  private boolean reachable(UUID source, UUID target, UUID boardId) {
    Set<UUID> visited = new HashSet<>();
    Deque<UUID> queue = new ArrayDeque<>();
    queue.add(source);
    visited.add(source);
    while (!queue.isEmpty()) {
      UUID cursor = queue.poll();
      if (Objects.equals(cursor, target)) return true;
      for (TabRelationEntity edge : relationRepo.findByFromTabIdAndDeletedAtIsNull(cursor)) {
        if (!Objects.equals(edge.getBoardId(), boardId)) continue;
        UUID next = edge.getToTabId();
        if (visited.add(next)) {
          queue.add(next);
        }
      }
    }
    return false;
  }

  private WorkspaceContext requirePersonalWritable() {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
      throw new IllegalStateException("PERSONAL scope required");
    }
    if (!ctx.canWrite()) {
      throw new IllegalStateException("VIEWER cannot mutate relations");
    }
    return ctx;
  }
}
