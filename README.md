# Laminar

연구실 일정·실험 샘플 관리 웹 서비스. Obsidian Laminar 플러그인의 웹 리빌드.

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

## 라이선스

`PROPRIETARY` — 연구실 내부용. `LICENSE` 파일 참조.
