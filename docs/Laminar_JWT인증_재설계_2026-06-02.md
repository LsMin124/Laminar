# Laminar JWT 인증 재구성 — 설계 청사진 (핸드오프)

- 작성일: 2026-06-02
- 상태: **다음 세션 착수 예정** (이번 세션은 stopgap만 적용)
- 목적: bespoke 세션 인증(반복 취약 — 로그아웃 무력화·로컬 401)을 표준 JWT 패턴으로 대체

## 배경 / 진단 (2026-06-02)
- prod 라이브 probe: signup 200 · me 200 · login 200 → **prod 인증 정상**.
- 사용자 보고 "회원가입/로그인 401"은 **로컬 환경 문제**. 유력 원인: `app.cookie.secure` 기본 true(로컬 오버라이드 없었음) → 로컬 http에서 Secure 쿠키 미지속 → 무인증 401. (DB/secret 없어 재현 미확정)
- **Stopgap(적용됨)**: `application-local.yml`에 `app.cookie.secure: false`. 로컬 즉시 unblock.
- 근본 해결 = 아래 JWT 재구성.

## 현재 구조 (대체 대상)
- `SessionAuthenticationFilter`: `laminar-session` 쿠키 → SHA-256 해시 → `sessions` 테이블 조회 → SecurityContext. 무효 시 anonymous→401.
- `SessionService`: 토큰 발급·revoke (sessions row, 28d TTL).
- `SecurityConfig`: stateless SecurityContext(`RequestAttributeSecurityContextRepository`) + `CsrfHeaderFilter`(X-Laminar-CSRF) + `RateLimitFilter`.
- `AuthController`: signup/login → `SessionService.issue` + 쿠키; logout → revoke + 쿠키삭제 + 세션 invalidate.
- `OAuth2LoginSuccessHandler`: 동일 세션 발급.

## 목표 표준 설계
| 요소 | 선택 |
|---|---|
| 라이브러리 | `io.jsonwebtoken:jjwt-api/impl/jackson` 0.12.x |
| 토큰 | Access(단명 ~15m, stateless JWT) + Refresh(~28d) |
| 저장/전송 | 둘 다 httpOnly + Secure(prod) + SameSite=Lax 쿠키 (프론트 credentials:include 이미 됨) |
| 검증 | `JwtAuthenticationFilter`가 서명·만료만 (DB 조회 0 = stateless) → SessionAuthenticationFilter 대체 |
| 발급 | login/signup이 BCrypt 검증 후 발급 (기존 users·비번 해시 무손실), OAuth2 핸들러도 JWT |
| 갱신/폐기 | `/api/auth/refresh`(refresh 쿠키→새 access); logout=쿠키삭제(+refresh revoke) |
| 서명 | HS256, `JWT_SECRET` (Fly secret, ≥256bit, 평문 커밋 금지) |

## 구현 단계 (백 → 프론트)
1. 의존성 추가: jjwt-api/impl/jackson.
2. `JwtService`: access/refresh 발급·검증. claims sub=userId, email, roles. 알고리즘 고정(none 공격 방지)·exp 검증.
3. `JwtAuthenticationFilter`: access 쿠키 → 검증 → `LaminarPrincipal`로 SecurityContext(무DB). 빌트인 필터 앞 배치(addFilterBefore 참조는 등록된 빌트인 필터로 — 과거 부팅크래시 교훈).
4. `SecurityConfig`: 필터 교체. CSRF 헤더·RateLimit 유지.
5. `AuthController`: signup/login → access+refresh 쿠키. `/auth/refresh`. logout → 쿠키삭제+refresh revoke.
6. Refresh 폐기: `sessions` 테이블을 refresh 토큰(해시)·rotation·revoke 용도로 전환 권장(탈취 대응). V16 마이그레이션.
7. `OAuth2LoginSuccessHandler`: 세션 대신 JWT 쿠키 발급.
8. 프론트 `lib/api.ts`: `401 → /auth/refresh → 원요청 재시도` 인터셉터(쿠키 httpOnly라 JS 토큰 미접근), refresh single-flight(동시요청 중복방지).
9. 검증: 로컬 bootRun(docker-compose DB) + 배포 후 prod probe(signup→me→refresh→logout).

## 저장소 특이사항 (주의)
- git 토큰에 `workflow` 스코프 없음 → `.github/workflows/ci.yml` 직접수정 불가.
- CI는 `GITHUB_ACTIONS` 감지 시 Testcontainers IT 제외(`build.gradle.kts`); auth IT는 로컬 docker-compose로 검증. (TODO: CI에 Testcontainers 연결 — 별건)
- 증분 commit/push/배포(`flyctl deploy --remote-only -a laminar-prod`), 한국어 conventional.
- GateGuard 유지(끄지 말 것). 평문 시크릿·.env 커밋 금지.
