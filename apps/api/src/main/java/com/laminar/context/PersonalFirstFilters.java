package com.laminar.context;

/**
 * Hibernate @Filter 이름 상수.
 *
 * 엔티티는 @FilterDef + @Filter로 이 이름을 참조. HibernateFilterActivator가
 * 매 Session 시작 시 WorkspaceContext에 맞춰 활성화 + 파라미터 바인딩.
 *
 *   - PERSONAL_FIRST: workspace_id + user_id 이중 필터 (Personal-First)
 *   - WORKSPACE_SHARED: workspace_id 단일 필터 (audit, equipment, outbox 등)
 *
 * SYSTEM scope 엔티티 (users / sessions / shedlock / email_outbox)는 @Filter 미부착.
 */
public final class PersonalFirstFilters {

    public static final String PERSONAL_FIRST = "personalFirstFilter";
    public static final String WORKSPACE_SHARED = "workspaceSharedFilter";

    public static final String PARAM_WORKSPACE_ID = "ctxWorkspaceId";
    public static final String PARAM_USER_ID = "ctxUserId";

    private PersonalFirstFilters() {
    }
}
