package com.laminar.context;

import com.laminar.subject.domain.SubjectRole;
import java.util.UUID;

/**
 * 요청 단위 격리 컨텍스트.
 *
 * <p>3계층: - SYSTEM: subjectId·userId 둘 다 null (cron, shedlock, email_outbox, users-self) -
 * SUBJECT_SHARED: subjectId만 (audit_log, equipment 시리즈, jobs_outbox) - PERSONAL: subjectId + userId
 * 모두 set (cards/boards 등 Personal-First)
 *
 * <p>scope()는 set된 필드 조합으로 즉시 도출.
 */
public record SubjectContext(UUID subjectId, UUID userId, SubjectRole userRole) {

  public enum Scope {
    SYSTEM,
    SUBJECT_SHARED,
    PERSONAL
  }

  public static SubjectContext system() {
    return new SubjectContext(null, null, null);
  }

  public static SubjectContext subject(UUID subjectId) {
    if (subjectId == null) {
      throw new IllegalArgumentException("subject context requires subjectId");
    }
    return new SubjectContext(subjectId, null, null);
  }

  public static SubjectContext personal(UUID subjectId, UUID userId, SubjectRole role) {
    if (subjectId == null || userId == null) {
      throw new IllegalArgumentException("personal context requires subjectId and userId");
    }
    return new SubjectContext(subjectId, userId, role);
  }

  public Scope scope() {
    if (subjectId == null) return Scope.SYSTEM;
    if (userId == null) return Scope.SUBJECT_SHARED;
    return Scope.PERSONAL;
  }

  public boolean isOwner() {
    return userRole == SubjectRole.OWNER;
  }

  public boolean canWrite() {
    return userRole == SubjectRole.OWNER || userRole == SubjectRole.MEMBER;
  }

  /**
   * Personal-First 엔티티(subject_id + user_id) 소유권 검증. Hibernate @Filter는 findById(PK 로드)에 적용되지 않으므로
   * 단건 접근 시 명시 호출. 컨텍스트가 PERSONAL이고 두 ID 모두 일치할 때만 true (fail-closed).
   */
  public boolean ownsPersonal(UUID entitySubjectId, UUID entityUserId) {
    return subjectId != null
        && userId != null
        && subjectId.equals(entitySubjectId)
        && userId.equals(entityUserId);
  }

  /** Subject-Shared 엔티티(subject_id) 소유권 검증. 컨텍스트 workspace와 엔티티 subject 일치 시 true (fail-closed). */
  public boolean ownsShared(UUID entitySubjectId) {
    return subjectId != null && subjectId.equals(entitySubjectId);
  }
}
