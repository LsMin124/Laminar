/**
 * Laminar API client — fetch wrapper.
 *
 * Cookie 기반 세션 (credentials: include) + X-Laminar-Subject-Id 헤더 자동 주입.
 * 백엔드 SubjectContextRequestFilter가 주제(subject) 컨텍스트로 진입.
 */

import { markAuthenticated, registerRefresher, stopSilentRefresh } from "./silentRefresh";

const API_BASE = import.meta.env.VITE_API_BASE ?? "";

const SUBJECT_ID_KEY = "laminar.subjectId";
// DX-10 rename(workspace→subject) 이전 키 — 기존 사용자 세션 호환용. 읽기에서 새 키로 이관.
const LEGACY_WORKSPACE_ID_KEY = "laminar.workspaceId";

let currentSubjectId: string | null = null;

export function setCurrentSubjectId(subjectId: string | null): void {
  currentSubjectId = subjectId;
  if (subjectId) {
    localStorage.setItem(SUBJECT_ID_KEY, subjectId);
  } else {
    localStorage.removeItem(SUBJECT_ID_KEY);
  }
  localStorage.removeItem(LEGACY_WORKSPACE_ID_KEY);
}

export function getCurrentSubjectId(): string | null {
  if (currentSubjectId === null) {
    currentSubjectId = localStorage.getItem(SUBJECT_ID_KEY);
    if (currentSubjectId === null) {
      const legacy = localStorage.getItem(LEGACY_WORKSPACE_ID_KEY);
      if (legacy) setCurrentSubjectId(legacy); // 구 키 → 새 키 1회 이관
    }
  }
  return currentSubjectId;
}

export class ApiError extends Error {
  readonly status: number;
  readonly body: unknown;

  constructor(status: number, body: unknown, message: string) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

// 401 시 refresh를 시도하지 않을 경로 — refresh 자신(무한루프), 자격 자체를 다루는 login/signup/logout.
const AUTH_RETRY_SKIP = [
  "/api/auth/login",
  "/api/auth/signup",
  "/api/auth/refresh",
  "/api/auth/logout",
];

function skipAuthRetry(path: string): boolean {
  return AUTH_RETRY_SKIP.some((p) => path.startsWith(p));
}

let refreshInFlight: Promise<boolean> | null = null;

/**
 * access 만료(반응적 401)·만료 임박(선제 타이머) 시 refresh 쿠키로 새 토큰쌍 발급. 탭 내
 * single-flight — 동시에 401이 다발해도 refresh 요청은 1회만 나가고 모든 대기자가 그 결과를
 * 공유한다. 쿠키는 httpOnly라 JS가 토큰을 직접 만지지 않는다.
 */
function attemptRefresh(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = exclusiveRefresh().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

const LAST_REFRESH_AT_KEY = "laminar.lastRefreshAt";
// 다른 탭이 방금 회전시킨 토큰쌍을 또 회전시키지 않기 위한 창 — access TTL(15m)보다 충분히 짧게.
const REFRESH_DEDUPE_MS = 30_000;

/**
 * 탭 간 직렬화 + 디듀프. refresh는 회전식(기존 refresh 즉시 폐기)이라 두 탭이 같은 토큰으로 동시
 * 호출하면 늦은 쪽이 401을 맞는다 — Web Locks로 동일 오리진 탭을 직렬화하고, 락을 얻은 시점에 다른
 * 탭이 방금 갱신했으면(stamp) 호출을 건너뛴다(브라우저 쿠키 항아리는 이미 새 토큰). Locks 미지원
 * 브라우저는 종전 동작(드문 경쟁 수용).
 */
function exclusiveRefresh(): Promise<boolean> {
  const run = async (): Promise<boolean> => {
    const last = Number(localStorage.getItem(LAST_REFRESH_AT_KEY) ?? "0");
    if (Date.now() - last < REFRESH_DEDUPE_MS) return true;
    return refreshWithRetry();
  };
  if (typeof navigator !== "undefined" && navigator.locks) {
    return navigator.locks.request("laminar.auth.refresh", run) as Promise<boolean>;
  }
  return run();
}

/**
 * refresh 1회 + 일시 오류(머신 suspend 재개 등 네트워크/5xx) 시 1회 재시도.
 * 깨끗한 401(refresh 토큰이 실제로 무효)은 재시도 없이 false → 로그아웃 처리한다.
 */
async function refreshWithRetry(): Promise<boolean> {
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const res = await fetch(`${API_BASE}/api/auth/refresh`, {
        method: "POST",
        headers: { "X-Laminar-CSRF": "1" },
        credentials: "include",
      });
      if (res.ok) {
        localStorage.setItem(LAST_REFRESH_AT_KEY, String(Date.now()));
        try {
          const body = (await res.json()) as { accessTtlSeconds?: number };
          if (body.accessTtlSeconds) markAuthenticated(body.accessTtlSeconds);
        } catch {
          // 본문 파싱 실패는 치명 아님 — 선제 타이머가 멈춰도 반응적 401 경로가 안전망
        }
        return true;
      }
      if (res.status === 401) {
        stopSilentRefresh(); // 세션 종료 — 다음 인증까지 선제 갱신 침묵
        return false; // refresh 토큰 무효 → 진짜 만료
      }
      // 그 외(5xx 등 일시 오류)는 재시도 루프로
    } catch {
      // 네트워크 오류 → 재시도
    }
    if (attempt === 0) await new Promise((r) => setTimeout(r, 800));
  }
  return false;
}

// 선제 갱신 타이머의 refresher 주입 — silentRefresh가 api.ts를 역import하면 순환이라 방향 고정.
registerRefresher(attemptRefresh);

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  opts?: { skipSubjectHeader?: boolean },
): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    // M-1 CSRF: 백엔드 CsrfHeaderFilter가 쿠키 기반 상태변경 요청에 이 custom header를 강제.
    // 교차출처 위조 요청은 CORS preflight 없이 커스텀 헤더를 달 수 없어 차단된다.
    "X-Laminar-CSRF": "1",
  };
  const subjectId = getCurrentSubjectId();
  // 주제 목록 조회(GET /api/subjects)는 SYSTEM scope여야 한다 — 헤더가 있으면 subjectSharedFilter가
  // 활성 주제 1개로 제한해 새 주제가 목록에서 누락된다. 이 경우만 skipSubjectHeader로 헤더를 생략.
  if (subjectId && !opts?.skipSubjectHeader) {
    headers["X-Laminar-Subject-Id"] = subjectId;
  }

  const serializedBody = body === undefined ? undefined : JSON.stringify(body);
  const doFetch = () =>
    fetch(`${API_BASE}${path}`, {
      method,
      headers,
      credentials: "include",
      body: serializedBody,
    });

  let response = await doFetch();

  // access 만료(401) → refresh 1회 시도 후 원요청 재시도. 성공 시 새 access 쿠키로 재요청이 통과한다.
  if (response.status === 401 && !skipAuthRetry(path)) {
    const refreshed = await attemptRefresh();
    if (refreshed) {
      response = await doFetch();
    }
  }

  if (!response.ok) {
    const text = await response.text();
    let payload: unknown = text;
    try {
      payload = JSON.parse(text);
    } catch {
      // text 그대로
    }
    throw new ApiError(
      response.status,
      payload,
      `${method} ${path} → ${response.status}`,
    );
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const api = {
  get: <T>(path: string, opts?: { skipSubjectHeader?: boolean }) =>
    request<T>("GET", path, undefined, opts),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, body),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  delete: <T>(path: string) => request<T>("DELETE", path),
};
