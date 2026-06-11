package com.laminar.subject.domain;

import com.laminar.common.domain.SubjectScopedBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * LAB 가입 신청 — 초대코드 입력 시 pending 생성, ADMIN+가 승인(멤버 INSERT)/거절(LAB재설계 §2). createdAt이 신청 시각.
 *
 * <p>중복 pending은 부분 유니크 인덱스(uq_lab_join_requests_pending)가 차단 — 서비스는 기존 pending을 멱등 반환한다.
 */
@Entity
@Table(name = "lab_join_requests")
@Filter(name = "subjectSharedFilter", condition = "subject_id = :ctxSubjectId")
@Getter
@Setter
public class LabJoinRequestEntity extends SubjectScopedBaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "status", nullable = false)
  private LabJoinStatus status = LabJoinStatus.PENDING;

  @Column(name = "decided_by")
  private UUID decidedBy;

  @Column(name = "decided_at")
  private OffsetDateTime decidedAt;
}
