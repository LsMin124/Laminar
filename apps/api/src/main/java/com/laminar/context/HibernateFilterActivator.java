package com.laminar.context;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * 트랜잭션 시작 직후 Hibernate @Filter를 활성화한다.
 *
 *   - WorkspaceContext가 PERSONAL: workspaceId + userId 필터 둘 다 활성화
 *   - WORKSPACE_SHARED: workspaceId 필터만
 *   - SYSTEM: 필터 미활성화 (전체 read 허용 — SystemRepository에서만 사용)
 *
 * Repository에서 read-only 트랜잭션 진입 시 자동 적용.
 * 트랜잭션 종료 시 자동 정리 (Hibernate Session 닫힘과 동시).
 */
@Component
public class HibernateFilterActivator {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Repository 메서드 진입 시 Aspect 또는 TransactionSync에서 호출.
     * Session 단위로 1회 적용하면 충분 — 이미 활성화된 필터는 idempotent.
     */
    public void activate() {
        Session session = entityManager.unwrap(Session.class);
        WorkspaceContext context = WorkspaceContextHolder.get();
        if (context == null || context.scope() == WorkspaceContext.Scope.SYSTEM) {
            return;
        }

        UUID workspaceId = context.workspaceId();
        if (workspaceId == null) {
            return;
        }

        enableWorkspaceShared(session, workspaceId);

        if (context.scope() == WorkspaceContext.Scope.PERSONAL && context.userId() != null) {
            enablePersonalFirst(session, workspaceId, context.userId());
        }
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

    /**
     * Spring TransactionSynchronization에 등록되어 트랜잭션 시작 시점에 필터 활성화.
     * Aspect 대신 Synchronization을 쓰는 이유: AOP proxy chain에 의존하지 않아
     * @Transactional이 붙은 모든 메서드에서 자동 동작.
     */
    public void registerForCurrentTransaction() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                activate();
            }
        });
        activate();
    }
}
