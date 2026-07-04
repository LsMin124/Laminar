package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.context.SubjectRole;
import com.laminar.subject.domain.SubjectEntity;
import com.laminar.subject.domain.SubjectMemberEntity;
import com.laminar.subject.domain.SubjectMemberId;
import com.laminar.subject.repository.SubjectMemberRepository;
import com.laminar.subject.repository.SubjectRepository;
import com.laminar.system.UserSystemRepository;
import com.laminar.tab.application.TabService;
import com.laminar.user.domain.UserEntity;
import com.laminar.whiteboard.application.WhiteboardEdgeService;
import com.laminar.whiteboard.application.WhiteboardGraphService;
import com.laminar.whiteboard.application.WhiteboardNodeService;
import com.laminar.whiteboard.domain.WhiteboardNodeEntity;
import com.laminar.whiteboard.domain.WhiteboardNodeKind;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** 화이트보드 도메인 통합 테스트 — Flyway V33 실적용(로컬 -PexcludeIT는 미실행, CI가 정본)으로 스키마·엔티티 매핑·격리 필터를 검증한다. */
class WhiteboardServiceIT extends IsolationIntegrationBase {

  @Autowired TabService tabService;
  @Autowired WhiteboardNodeService nodeService;
  @Autowired WhiteboardEdgeService edgeService;
  @Autowired WhiteboardGraphService graphService;
  @Autowired UserSystemRepository userRepo;
  @Autowired SubjectRepository subjectRepo;
  @Autowired SubjectMemberRepository memberRepo;
  @Autowired HibernateFilterActivator filterActivator;

  private UUID userA;
  private UUID subjectId;
  private UUID tabId;

  @BeforeEach
  void seed() {
    SubjectContextHolder.clear();
    UserEntity a = new UserEntity();
    a.setEmail("wb-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    SubjectEntity ws = new SubjectEntity();
    ws.setName("WB WS");
    ws.setSlug("wb-ws-" + UUID.randomUUID());
    ws.setOwnerUserId(userA);
    ws.setDefaultTimezone("Asia/Seoul");
    ws.setSettings(new HashMap<>());
    subjectId = subjectRepo.save(ws).getId();

    SubjectMemberEntity m = new SubjectMemberEntity();
    m.setId(new SubjectMemberId(subjectId, userA));
    m.setRole(SubjectRole.OWNER);
    memberRepo.save(m);

    SubjectContextHolder.set(SubjectContext.personal(subjectId, userA, SubjectRole.OWNER));
    filterActivator.activate();
    tabId = tabService.create("WB", "wb-" + UUID.randomUUID(), null, null, null, null).getId();
  }

  @AfterEach
  void cleanup() {
    SubjectContextHolder.clear();
  }

  @Test
  @Transactional
  void node_persists_and_lists_by_tab() {
    nodeService.create(mdNode(10, 20));
    assertThat(nodeService.listByTab(tabId)).hasSize(1);
  }

  @Test
  @Transactional
  void node_move_updates_free_coordinates() {
    WhiteboardNodeEntity node = nodeService.create(mdNode(10, 20));
    nodeService.update(
        node.getId(),
        new WhiteboardNodeService.UpdateInput(300.0, 400.0, null, null, null, null, null));
    WhiteboardNodeEntity moved = nodeService.findById(node.getId()).orElseThrow();
    assertThat(moved.getX()).isEqualTo(300.0);
    assertThat(moved.getY()).isEqualTo(400.0);
  }

  @Test
  @Transactional
  void edge_self_loop_rejected() {
    WhiteboardNodeEntity n = nodeService.create(mdNode(0, 0));
    assertThatThrownBy(() -> edgeService.create(n.getId(), n.getId(), null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("from_node_id == to_node_id");
  }

  @Test
  @Transactional
  void edge_requires_same_tab() {
    WhiteboardNodeEntity n1 = nodeService.create(mdNode(0, 0));
    UUID otherTabId =
        tabService.create("WB2", "wb2-" + UUID.randomUUID(), null, null, null, null).getId();
    WhiteboardNodeEntity n2 =
        nodeService.create(
            new WhiteboardNodeService.CreateInput(
                otherTabId, WhiteboardNodeKind.MD, 5, 5, null, null, null, null, null));

    assertThatThrownBy(() -> edgeService.create(n1.getId(), n2.getId(), null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("share a tab");
  }

  @Test
  @Transactional
  void graph_returns_nodes_and_edges() {
    WhiteboardNodeEntity n1 = nodeService.create(mdNode(0, 0));
    WhiteboardNodeEntity n2 = nodeService.create(mdNode(100, 100));
    edgeService.create(n1.getId(), n2.getId(), null, "의존", null);

    WhiteboardGraphService.WhiteboardGraph g = graphService.getGraph(tabId);
    assertThat(g.nodes()).hasSize(2);
    assertThat(g.edges()).hasSize(1);
    assertThat(g.edges().get(0).getLabel()).isEqualTo("의존");
  }

  private WhiteboardNodeService.CreateInput mdNode(double x, double y) {
    return new WhiteboardNodeService.CreateInput(
        tabId, WhiteboardNodeKind.MD, x, y, null, null, null, null, null);
  }
}
