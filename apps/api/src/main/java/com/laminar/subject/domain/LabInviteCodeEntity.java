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
 * LAB 초대코드 — lab당 활성 1개(회전식: 재발급 시 기존 revoke). 코드 입력 가입은 관리자 승인 큐를 거친다(LAB재설계 §2).
 *
 * <p>코드 자체는 사람이 입력하는 짧은 문자열이라 토큰 해시 정책(세션·초대 토큰) 대상이 아님 — 유출 대응은 회전(revoke)이 정본.
 */
@Entity
@Table(name = "lab_invite_codes")
@Filter(name = "subjectSharedFilter", condition = "subject_id = :ctxSubjectId")
@Getter
@Setter
public class LabInviteCodeEntity extends SubjectScopedBaseEntity {

  @Column(name = "code", nullable = false, unique = true)
  private String code;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;
}
