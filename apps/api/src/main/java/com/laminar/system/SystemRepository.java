package com.laminar.system;

/**
 * 시스템 컨텍스트 (격리 우회) 마커 인터페이스.
 *
 * 이 인터페이스를 구현하는 Repository만 @Filter 미적용 read/write 허용 — cron,
 * outbox flush, audit append, user/session lookup 등 워크스페이스 무관 작업.
 *
 * ArchUnit 룰이 강제:
 *   - SystemRepository 구현체는 com.laminar.system 패키지에만 위치
 *   - SystemRepository는 com.laminar.web.controller에서 import 금지 (3계층 우회 방지)
 *
 * 사용처:
 *   - UserSystemRepository: 글로벌 사용자 조회 (워크스페이스 무관)
 *   - SessionSystemRepository: Auth.js DB session adapter
 *   - JobsOutboxSystemRepository: 워커 polling
 *   - EmailOutboxSystemRepository: flush cron
 *   - ShedlockSystemRepository: 분산 락
 *   - AuditLogSystemRepository: append-only audit (cron cleanup만 hard delete)
 */
public interface SystemRepository {
}
