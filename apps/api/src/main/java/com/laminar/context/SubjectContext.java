package com.laminar.context;

import java.util.UUID;

/**
 * 요청 단위 격리 컨텍스트.
 *
 * <p>3계층: - SYSTEM: subjectId·userId 둘 다 null (cron, shedlock, email_outbox, users-self) -
 * SUBJECT_SHARED: subjectId만 (audit_log, jobs_outbox) - PERSONAL: subjectId + userId 모두 set
 * (cards/tabs 등 Personal-First)
 *
 * <p>scope()는 set된 필드 조합으로 즉시 도출. subjectKind = 주제 종별(personal|lab) — 장비·가입 흐름 등 lab 전용 표면이
 * isLab()으로 판정한다(LAB재설계 §1.1).
 */
public record SubjectContext(
    UUID subjectId, UUID userId, SubjectRole userRole, SubjectKind subjectKind) {

  public enum Scope {
    SYSTEM,
    SUBJECT_SHARED,
    PERSONAL
  }

  public static SubjectContext system() {
    return new SubjectContext(null, null, null, null);
  }

  public static SubjectContext subject(UUID subjectId) {
    if (subjectId == null) {
      throw new IllegalArgumentException("subject context requires subjectId");
    }
    return new SubjectContext(subjectId, null, null, null);
  }

  /** kind 미지정 오버로드 — PERSONAL 종별 기본. lab 전용 표면은 4-인자 팩토리로만 통과(fail-closed). */
  public static SubjectContext personal(UUID subjectId, UUID userId, SubjectRole role) {
    return personal(subjectId, userId, role, SubjectKind.PERSONAL);
  }

  public static SubjectContext personal(
      UUID subjectId, UUID userId, SubjectRole role, SubjectKind kind) {
    if (subjectId == null || userId == null) {
      throw new IllegalArgumentException("personal context requires subjectId and userId");
    }
    return new SubjectContext(subjectId, userId, role, kind);
  }

  public Scope scope() {
    if (subjectId == null) return Scope.SYSTEM;
    if (userId == null) return Scope.SUBJECT_SHARED;
    return Scope.PERSONAL;
  }

  public boolean isOwner() {
    return userRole == SubjectRole.OWNER;
  }

  /** OWNER‖ADMIN — lab 관리 표면(장비 CRUD·초대·가입 승인·멤버 제거) 가드 (LAB재설계 §1.3). */
  public boolean isAdmin() {
    return userRole == SubjectRole.OWNER || userRole == SubjectRole.ADMIN;
  }

  /** 활성 주제가 LAB인가 — 장비 등 lab 전용 표면 진입 판정. */
  public boolean isLab() {
    return subjectKind == SubjectKind.LAB;
  }

  /** 역할 보유 = 쓰기 가능(3등급 전부) — 읽기 전용 등급(구 VIEWER)은 V29에서 퇴역. 무역할 컨텍스트만 차단. */
  public boolean canWrite() {
    return userRole != null;
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

  /**
   * Owner-Scoped 엔티티(사용자 단일 소유) 검증 — 장비 시리즈처럼 주제와 무관하게 사용자 전체에 통합되는 자원용. 컨텍스트 user와 엔티티의 소유
   * 사용자(created_by/reserved_by 등) 일치 시 true (fail-closed). L3 장비 lab 재스코프와 함께 퇴역 예정.
   */
  public boolean ownsUser(UUID entityUserId) {
    return userId != null && userId.equals(entityUserId);
  }
}
