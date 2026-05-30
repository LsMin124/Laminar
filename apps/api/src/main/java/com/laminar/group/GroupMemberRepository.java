package com.laminar.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * 그룹 ↔ 카드 N:N junction.
 *
 * workspace_id 컬럼 없어 Hibernate @Filter 미부착 — parent (group/card) 격리에 의존.
 * 격리 검증 책임은 service layer (groupId/cardId가 현재 user 자원인지 확인 후 INSERT).
 */
public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, GroupMemberId> {

    List<GroupMemberEntity> findByIdGroupId(UUID groupId);

    List<GroupMemberEntity> findByIdCardId(UUID cardId);

    /** 보드 그래프용 — 여러 그룹의 멤버십 1회 조회 (P3b 자동그룹). */
    List<GroupMemberEntity> findByIdGroupIdIn(List<UUID> groupIds);
}
