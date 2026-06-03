package com.laminar.tab.repository;

import com.laminar.tab.domain.TabMemberEntity;
import com.laminar.tab.domain.TabMemberId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 탭 ↔ 카드 junction.
 *
 * <p>workspace_id 없어 @Filter 미부착 — parent (tab/card) 격리에 의존. priority는 (tab, card) 단위로 순서 유지.
 */
public interface TabMemberRepository extends JpaRepository<TabMemberEntity, TabMemberId> {

  List<TabMemberEntity> findByIdTabIdOrderByPriorityAsc(UUID tabId);

  List<TabMemberEntity> findByIdCardId(UUID cardId);

  Optional<TabMemberEntity> findFirstByIdTabIdOrderByPriorityDesc(UUID tabId);
}
