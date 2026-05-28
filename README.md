# Laminar

연구실 일정·실험 샘플 관리 웹 서비스. Obsidian Laminar 플러그인의 웹 리빌드.

> **백엔드 MVP 완료** (2026-05-29, Phase 0~13) — 인증·도메인 48 엔티티·관계 시각화·R2 storage·cron·RRULE·운영 콘솔. 프론트엔드 진입.

## 프로젝트 문서

코드 외 명세·런북은 별도 vault(`obsidian_ws/`)에서 관리한다:

| 문서 | 내용 |
|------|------|
| `Laminar_Spec.md` | 전체 명세·도메인 모델·로드맵 |
| `Laminar_DataModel.md` | DB 스키마·비즈니스 규칙 |
| `Laminar_Implementation.md` | API·인증·Phase 0~14 task |
| `Laminar_Phase0_Runbook.md` | 인프라 셋업·운영 가이드 |
| `Laminar_Archive.md` | 회수·결정 이력 |

## 스택

Java 21 (Temurin) · Spring Boot 3.5 (`apps/api`) · React 19 · Vite (`apps/web`)
배포 Fly.io (NRT) · DB Neon Postgres · Object Storage Cloudflare R2 · Email Resend · Auth Google OAuth · Monitoring Sentry

## 빠른 시작

```bash
# 백엔드
cd apps/api && ./gradlew bootRun

# 프론트엔드
cd apps/web && pnpm dev
```

세부 셋업은 `Laminar_Phase0_Runbook.md` 참조.

## 백엔드 기능 (Phase 0~13)

### 인프라
- DB Flyway V1~V12 (Postgres 17, CITEXT/JSONB/UUID/BTREE_GIST/EXCLUDE)
- Spring Security baseline + Auth.js DB session adapter 호환 (Cookie + 28일 TTL)
- Hibernate `@Filter` 3계층 격리 (SYSTEM / WORKSPACE_SHARED / PERSONAL) — 23개 엔티티에 적용
- ArchUnit 6 룰 (레이어드 + EntityManager 격리 + SystemRepository 우회 방지)

### 도메인
- 사용자·세션·워크스페이스·초대 (7일 TTL, SHA-256 token)
- 보드·카드 CRUD + 캘린더 뷰 (멀티데이 overlap + date_memos)
- 그룹·탭 (parent_tab tree, ≤10 depth + DAG 사이클 차단)
- 카드·그룹·탭 관계 (화살표 시각화, 보드 그래프 1회 fetch)
- 영구노트 tree + 동적 컬럼 (text/dropdown/checkbox) + 버전 (git commit 의미, is_current_diff 단일 row 보장)
- 첨부 (R2 presigned URL, 20MB 한도, sha256+size finalize)
- date_memos (board+user+date upsert)
- Sample Manager 멱등 import (card+sample+step unique)
- 공용 자원 (장비·예약 시간 겹침 차단·log 시트 동적 컬럼·공용 캘린더·공지)
- GCal 메타 + ImportJob 상태 머신

### 인프라
- 마크다운 → safe HTML (commonmark + OWASP sanitizer, 250KB 한도)
- 감사 로그 90일 보존 + cleanup cron
- ShedLock + 4 cron worker (outbox 30s · cleanup daily 03:00 KST · email flush 60s · RRULE expand daily 04:00 KST 90일 window)
- RRULE parser (RFC 5545 subset: FREQ/INTERVAL/COUNT/UNTIL) + 멱등 expand
- 운영 콘솔 (OWNER 강한 격리 + escape hatch reason ≥10자 + severity=high audit)

### 테스트
- ArchUnit 6 룰 + Markdown XSS 방지 7종 + RRULE 9 unit + WorkspaceContext 격리 매트릭스 5종 unit
- Testcontainers Postgres 통합 테스트 (CI Docker runner — Board·Card·Group·Tab·Perpetual·Attachment·DateMemo·SM·Audit·Outbox·Import·Relation IT)

## 라이선스

`PROPRIETARY` — 연구실 내부용. `LICENSE` 파일 참조.
