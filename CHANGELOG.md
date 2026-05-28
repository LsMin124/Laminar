# Changelog

## 2026-05-29 — 백엔드 MVP (Phase 0~13)

### Phase 0~1 — 인프라
- Spring Boot 3.5 + Java 21 + Gradle Kotlin DSL
- React 19 + Vite + TypeScript (apps/web placeholder)
- GitHub Actions CI + ArchUnit (layered + system 격리)
- Fly.io · Neon · R2 · Sentry 설정 backbone

### Phase 2 — DB 스키마
- Flyway V1~V12 (Postgres 17)
- 12 migrations: users·sessions·workspaces·boards · cards · relations·groups · tabs·perpetual · attachments·misc · audit_log · gcal · outbox·import_jobs · shedlock·sm_keys · 공용 자원 7 테이블 · display_order→priority INT 일괄 전환

### Phase 3 — 도메인 모델 + Repository 격리
- JPA 엔티티 48종 (Personal-First user_id NN + Workspace-Shared + System scope)
- WorkspaceContext 3계층 (Holder · RequestFilter · HibernateFilterActivator · @FilterDef)
- SystemRepository 마커 + ArchUnit 룰 3종 신규
- 격리 매트릭스 5종 unit 검증

### Phase 4 — 인증 + 워크스페이스
- Spring Security baseline (Cookie laminar-session + SessionAuthenticationFilter)
- UserService (BCrypt 12 round) + SessionService (28일 TTL)
- WorkspaceService (가입 직후 자동 생성) + InvitationService (7일 TTL · SHA-256 token · email_outbox 큐)
- WorkspaceContextRequestFilter Security 결합 (DB 멤버십 강제)
- 23개 엔티티 @Filter 일괄 적용
- Testcontainers cross-user 격리 통합 테스트

### Phase 5 — 보드·카드
- 보드 CRUD + priority 자동 + reorder
- 카드 CRUD + 7 importance + 4 origin + RRULE + 30일 한도 + perpetual-ver invariant
- 캘린더 뷰 endpoint (멀티데이 overlap + date_memos 통합)
- DnD reorder (board·card priority 배치 UPDATE)
- 마크다운 → safe HTML (commonmark + OWASP sanitizer, 250KB)

### Phase 6 — 관계 시각화
- 그룹 CRUD + GroupMember junction (양방향 lookup)
- 그룹·카드 관계 (self-ref 차단 + same-board 강제)
- 보드 그래프 API (1회 fetch — cards + groups + cardRelations + groupRelations)

### Phase 7 — 탭·영구노트
- 탭 CRUD (parent_tab_id tree ≤10 depth) + TabMember (priority)
- 탭 관계 (DAG 사이클 BFS 검출)
- 영구노트 tree + 동적 컬럼 (text/dropdown/checkbox) + 값 upsert
- 영구노트 버전 (version_number 자동 + is_current_diff 단일 row)
- 카드 perpetual-ver → 자동 commit (card_id 1:1)

### Phase 8 — 첨부·datememo·SM
- 첨부 메타 CRUD + 20MB 한도 + finalize (sha256/size)
- R2 S3Presigner (5분 TTL PUT/GET, workspaces/{ws}/users/{user}/ 격리 path)
- DateMemo (board+user+date upsert)
- SampleManagerLink 멱등 import + payload_snapshot

### Phase 9 — 감사·outbox·GCal 메타·import
- AuditLogService append-only + 90일 보존
- JobsOutboxService SKIP LOCKED + retry backoff (attempt^2 분)
- GCal: BoardCalendarLink (sync_direction + sync_token) + CardEventLink (etag + last_pushed_hash)
- ImportJob 5 status 전환 머신

### Phase 10 — 공용 자원
- Equipment CRUD + name unique + EquipmentAdmin N:N (OWNER만)
- EquipmentReservation 시간 겹침 차단 (start<end + 7일 + DB EXCLUDE 동기)
- SharedCalendar (equipment 1:1) + Announcement
- EquipmentLog 시트 5종 컬럼 (text/number/enum/bool/datetime) + 값 type 검증

### Phase 11 — Cron 인프라
- ShedLock JDBC + @EnableSchedulerLock
- JobsOutboxWorker 30초 + EmailOutboxFlushWorker 60초 + CleanupScheduler 03:00 KST 일별

### Phase 12 — RRULE expand
- RruleParser RFC 5545 subset (FREQ DAILY/WEEKLY/MONTHLY + INTERVAL + COUNT + UNTIL)
- RruleExpansionService 마스터→인스턴스 멱등 (attrs로 중복 방지)
- RruleExpansionWorker 04:00 KST 일별 90일 window

### Phase 13 — 운영 콘솔
- AdminWorkspaceService cross-user 메타뷰 (sanitize body 제거)
- Escape hatch reveal-body (reason ≥10자 + severity=high audit)
- 자동 audit append (admin.boards.list / cards.list_metadata / card.reveal_body)
