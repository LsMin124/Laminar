package com.laminar.subject.repository;

import com.laminar.subject.domain.LabInviteCodeEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** LAB 초대코드 — 관리자 측(lab PERSONAL 컨텍스트) 조회/회전용. 가입자 측 코드 조회는 system 레포(컨텍스트 밖 진입). */
public interface LabInviteCodeRepository extends JpaRepository<LabInviteCodeEntity, UUID> {

  Optional<LabInviteCodeEntity> findBySubjectIdAndRevokedAtIsNull(UUID subjectId);
}
