package com.laminar.context;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * 모든 Spring Data 리포지토리 메서드 실행 직전, 현재 Hibernate Session의 @Filter 상태를
 * WorkspaceContext에 동기화한다 (C-1 격리 결선).
 *
 * 리포지토리는 항상 @Transactional 서비스 내부(또는 SimpleJpaRepository 자체 @Transactional)에서
 * 호출되므로 이 시점엔 트랜잭션 Session이 열려 있다. 따라서 derived/list 쿼리
 * (findByBoardId 등)에 workspace_id(+user_id) 조건이 주입되어 교차 테넌트 행이 차단된다.
 *
 * 단건 PK 로드(findById)는 Hibernate 필터 비적용 대상이므로 도메인 서비스의 명시적
 * 소유권 검증이 별도 방어한다 (WorkspaceContext#ownsPersonal/#ownsShared).
 */
@Aspect
@Component
public class WorkspaceFilterAspect {

    private final HibernateFilterActivator filterActivator;

    public WorkspaceFilterAspect(HibernateFilterActivator filterActivator) {
        this.filterActivator = filterActivator;
    }

    @Before("execution(* com.laminar..*Repository+.*(..))")
    public void syncWorkspaceFilters() {
        filterActivator.applyForCurrentSession();
    }
}
