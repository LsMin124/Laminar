package com.laminar.subject.repository;

import com.laminar.subject.domain.SubjectMemberEntity;
import com.laminar.subject.domain.SubjectMemberId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Subject 멤버십 Repository (subject-shared scope).
 *
 * <p>활성 멤버 (removed_at IS NULL) 조회가 hot path — index idx_workspace_members_active.
 */
public interface SubjectMemberRepository
    extends JpaRepository<SubjectMemberEntity, SubjectMemberId> {

  Optional<SubjectMemberEntity> findByIdUserIdAndRemovedAtIsNull(UUID userId);

  /** 사용자의 모든 활성 워크스페이스 멤버십 — 워크스페이스 발견(가입 직후 진입)용. */
  List<SubjectMemberEntity> findAllByIdUserIdAndRemovedAtIsNull(UUID userId);

  Optional<SubjectMemberEntity> findByIdSubjectIdAndIdUserIdAndRemovedAtIsNull(
      UUID subjectId, UUID userId);

  List<SubjectMemberEntity> findByIdSubjectIdAndRemovedAtIsNull(UUID subjectId);
}
