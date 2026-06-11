package com.laminar.system;

import com.laminar.subject.domain.LabJoinRequestEntity;
import com.laminar.subject.domain.LabJoinStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * LAB 가입 신청의 시스템(컨텍스트 밖) 생성·중복 조회 — 신청자는 비멤버라 SYSTEM scope에서 진입한다(LabInviteCodeSystemRepository와 한
 * 쌍). 관리자 측 대기열 조회·판정은 subject.repository.LabJoinRequestRepository(컨텍스트 필터)가 담당.
 */
public interface LabJoinRequestSystemRepository
    extends JpaRepository<LabJoinRequestEntity, UUID>, SystemRepository {

  Optional<LabJoinRequestEntity> findBySubjectIdAndUserIdAndStatus(
      UUID subjectId, UUID userId, LabJoinStatus status);
}
