# Changelog

## 2026-05-29 — 프론트엔드 MVP (Phase 15~23)

### Phase 15 — 인증 + 워크스페이스 + 보드 목록
- React 19 + Vite + TanStack Query 셋업
- `lib/api.ts` (Cookie 세션 + workspace 헤더 자동 주입 + ApiError)
- LoginPage·SignupPage·BoardsPage + workspace localStorage persist

### Phase 16 — 보드 상세 + 캘린더 월 뷰 + 카드 상세
- react-router v7 라우터 (`/`·`/boards/:id`·`/boards/:id/cards/:cardId`)
- `lib/calendar.ts` 6주 그리드 + 멀티데이 lane overlap 알고리즘
- MonthGrid (today highlight · importance 컬러 · continues-left/right)
- CardDetailPage (서버측 OWASP sanitized HTML 표시)

### Phase 17 — 카드 CRUD UI + CodeMirror 에디터 + 첨부
- CodeMirror 6 minimal markdown editor
- CardForm + CardDialog (생성/편집 공용)
- AttachmentUploader (presigned PUT → finalize 3단계)

### Phase 18 — 그래프 시각화 + 멤버 초대 UI
- BoardGraph SVG circular layout (그룹 안쪽 · 카드 바깥쪽 · 화살표 marker)
- WorkspaceMemberController + listPending invitations (API)
- MembersPage 멤버 목록·역할·강퇴 + 초대 발송·토큰·취소

### Phase 19 — 영구노트 + 시트 컬럼 + 버전 diff
- 3-pane 페이지: 탭 tree / 노트 tree / 노트 상세
- 동적 시트 컬럼 6종 타입 (TEXT/NUMBER/DATE/BOOLEAN/ENUM/JSON)
- 버전 commit + currentDiff 토글 + diff 뷰

### Phase 20 — 그룹·관계 인라인 편집
- 그룹 CRUD + 멤버(카드) 토글 + 색상 picker
- 카드·그룹 관계 from/to select + kind·summary 인라인 생성/삭제
- 그래프 캐시 invalidate로 즉시 시각화 반영

### Phase 21 — 공용 자원 (장비 + 예약)
- EquipmentController + ReservationController (시간 겹침 차단)
- EquipmentPage 그리드 + create form + 활성화 토글
- EquipmentDetailPage datetime-local 예약 form + 30일 현황

### Phase 22 — 운영 콘솔
- AdminPage 전체 보드 메타 + 카드 메타 + 본문 reveal (사유 10자 검증)
- 최근 100건 audit log + severity 색상 분리 (high=red)

### Phase 23 — E2E + CI 보강
- Playwright config (chromium headless + E2E_DISABLE_BACKEND skip)
- 인증 화면 smoke + boards flow (백엔드 연결 시)
- e2e.yml workflow (PR 시 자동)
- deploy.yml은 사용자 명시 승인 후 별도 추가 (FLY_API_TOKEN 발급 + AUTO_DEPLOY var)

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
