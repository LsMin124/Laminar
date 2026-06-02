package com.laminar.tab;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 탭 ↔ 그룹 N:N junction (탭 멤버 = 그룹, 구상안 §3.3).
 *
 * <p>workspace_id 컬럼 없어 Hibernate @Filter 미부착 — 부모(tab/group) 격리에 의존. 격리 검증 책임은 service layer
 * (tabId/groupId가 현재 user 자원인지 확인 후 INSERT).
 */
public interface TabGroupMemberRepository
    extends JpaRepository<TabGroupMemberEntity, TabGroupMemberId> {

  List<TabGroupMemberEntity> findByIdTabId(UUID tabId);

  /** 보드 그래프용 — 여러 탭의 그룹 멤버십 1회 조회 (P4b 탭 스코프). */
  List<TabGroupMemberEntity> findByIdTabIdIn(List<UUID> tabIds);
}
