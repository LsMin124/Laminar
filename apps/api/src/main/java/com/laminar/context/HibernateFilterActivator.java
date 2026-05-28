package com.laminar.context;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * 현재 트랜잭션 Session의 Hibernate @Filter 상태를 WorkspaceContext에 동기화한다.
 *
 * Personal-First 격리는 @FilterDef/@Filter로 구현되며, 매 쿼리 실행 전 활성화돼야 한다.
 * {@code open-in-view:false}라 요청 필터 시점엔 Session이 없으므로, 트랜잭션 내부에서
 * 도는 {@code WorkspaceFilterAspect}가 리포지토리 호출 직전 {@link #applyForCurrentSession()}를
 * 호출해 결선한다.
 *
 *   - SYSTEM / 컨텍스트 없음: 두 필터 비활성 (cron·운영 콘솔 cross-user·인증 전)
 *   - WORKSPACE_SHARED: workspaceSharedFilter만 활성
 *   - PERSONAL: 두 필터(workspace_id + user_id) 활성
 *
 * 주의: Hibernate @Filter는 {@code session.find()}(PK 직접 로드)에는 적용되지 않는다.
 * 단건 UUID 접근은 도메인 서비스의 명시적 소유권 검증(WorkspaceContext#ownsPersonal 등)이
 * 최종 방어선이며, 본 필터는 derived/list 쿼리(findByBoardId 등)를 fail-closed로 차단한다.
 */
@Component
public class HibernateFilterActivator {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 활성 트랜잭션이 있을 때 현재 Session의 필터 상태를 WorkspaceContext에 맞춰 동기화.
     * 트랜잭션이 없으면 skip — 그 경우 발생하는 단건 PK 로드는 어차피 필터 대상이 아니다.
     */
    public void applyForCurrentSession() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        WorkspaceContext context = WorkspaceContextHolder.get();

        if (context == null || context.scope() == WorkspaceContext.Scope.SYSTEM) {
            disable(session, PersonalFirstFilters.PERSONAL_FIRST);
            disable(session, PersonalFirstFilters.WORKSPACE_SHARED);
            return;
        }

        UUID workspaceId = context.workspaceId();
        enableWorkspaceShared(session, workspaceId);

        if (context.scope() == WorkspaceContext.Scope.PERSONAL && context.userId() != null) {
            enablePersonalFirst(session, workspaceId, context.userId());
        } else {
            disable(session, PersonalFirstFilters.PERSONAL_FIRST);
        }
    }

    /**
     * 기존 호출처(AdminWorkspaceService, RruleExpansionWorker) 호환 별칭 —
     * 현재 컨텍스트 기준으로 세션 필터를 동기화한다.
     */
    public void activate() {
        applyForCurrentSession();
    }

    private void enableWorkspaceShared(Session session, UUID workspaceId) {
        Filter filter = session.getEnabledFilter(PersonalFirstFilters.WORKSPACE_SHARED);
        if (filter == null) {
            filter = session.enableFilter(PersonalFirstFilters.WORKSPACE_SHARED);
        }
        filter.setParameter(PersonalFirstFilters.PARAM_WORKSPACE_ID, workspaceId);
        filter.validate();
    }

    private void enablePersonalFirst(Session session, UUID workspaceId, UUID userId) {
        Filter filter = session.getEnabledFilter(PersonalFirstFilters.PERSONAL_FIRST);
        if (filter == null) {
            filter = session.enableFilter(PersonalFirstFilters.PERSONAL_FIRST);
        }
        filter.setParameter(PersonalFirstFilters.PARAM_WORKSPACE_ID, workspaceId);
        filter.setParameter(PersonalFirstFilters.PARAM_USER_ID, userId);
        filter.validate();
    }

    private void disable(Session session, String filterName) {
        if (session.getEnabledFilter(filterName) != null) {
            session.disableFilter(filterName);
        }
    }
}
