package com.laminar.subject.repository;

import com.laminar.subject.domain.SubjectInvitationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 초대 토큰 조회 — accept 시 token_hash로 찾고, 멤버 관리 화면에서 워크스페이스별 목록. */
public interface SubjectInvitationRepository extends JpaRepository<SubjectInvitationEntity, UUID> {

  Optional<SubjectInvitationEntity> findByTokenHash(String tokenHash);

  List<SubjectInvitationEntity> findBySubjectIdAndAcceptedAtIsNullAndRevokedAtIsNull(
      UUID subjectId);
}
