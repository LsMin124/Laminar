package com.laminar.context;

/**
 * Hibernate @Filter 이름 상수.
 *
 * <p>엔티티는 @FilterDef + @Filter로 이 이름을 참조. HibernateFilterActivator가 매 Session 시작 시 SubjectContext에
 * 맞춰 활성화 + 파라미터 바인딩.
 *
 * <p>- PERSONAL_FIRST: subject_id + user_id 이중 필터 (Personal-First) - SUBJECT_SHARED: subject_id 단일
 * 필터 (audit, equipment, outbox 등)
 *
 * <p>SYSTEM scope 엔티티 (users / sessions / shedlock / email_outbox)는 @Filter 미부착.
 */
public final class PersonalFirstFilters {

  public static final String PERSONAL_FIRST = "personalFirstFilter";
  public static final String SUBJECT_SHARED = "subjectSharedFilter";

  /** 사용자(소유자) 단일 필터 — 장비 시리즈가 주제와 무관하게 사용자 전체에 통합되도록(추후 lab 단위로 전환). */
  public static final String OWNER_SCOPED = "ownerScopedFilter";

  public static final String PARAM_SUBJECT_ID = "ctxSubjectId";
  public static final String PARAM_USER_ID = "ctxUserId";

  private PersonalFirstFilters() {}
}
