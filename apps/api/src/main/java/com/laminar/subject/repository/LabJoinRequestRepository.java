package com.laminar.subject.repository;

import com.laminar.subject.domain.LabJoinRequestEntity;
import com.laminar.subject.domain.LabJoinStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** LAB 가입 신청 — 관리자 측(lab PERSONAL 컨텍스트) 대기열 조회/판정용. 신청 생성·중복 조회는 system 레포(비멤버 진입). */
public interface LabJoinRequestRepository extends JpaRepository<LabJoinRequestEntity, UUID> {

  List<LabJoinRequestEntity> findBySubjectIdAndStatusOrderByCreatedAtAsc(
      UUID subjectId, LabJoinStatus status);
}
