/**
 * Laminar API client — fetch wrapper.
 *
 * Cookie 기반 세션 (credentials: include) + X-Laminar-Workspace-Id 헤더 자동 주입.
 * 백엔드 WorkspaceContextRequestFilter가 워크스페이스 컨텍스트로 진입.
 */

const API_BASE = import.meta.env.VITE_API_BASE ?? "";

let currentWorkspaceId: string | null = null;

export function setCurrentWorkspaceId(workspaceId: string | null): void {
  currentWorkspaceId = workspaceId;
  if (workspaceId) {
    localStorage.setItem("laminar.workspaceId", workspaceId);
  } else {
    localStorage.removeItem("laminar.workspaceId");
  }
}

export function getCurrentWorkspaceId(): string | null {
  if (currentWorkspaceId === null) {
    currentWorkspaceId = localStorage.getItem("laminar.workspaceId");
  }
  return currentWorkspaceId;
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
 * access 만료 시 refresh 쿠키로 새 토큰쌍 발급. single-flight — 동시에 401이 다발해도 refresh 요청은 1회만 나가고
 * 모든 대기자가 그 결과를 공유한다. 쿠키는 httpOnly라 JS가 토큰을 직접 만지지 않는다.
 */
function attemptRefresh(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = fetch(`${API_BASE}/api/auth/refresh`, {
      method: "POST",
      headers: { "X-Laminar-CSRF": "1" },
      credentials: "include",
    })
      .then((res) => res.ok)
      .catch(() => false)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

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
  const workspaceId = getCurrentWorkspaceId();
  // 주제 목록 조회(GET /api/subjects)는 SYSTEM scope여야 한다 — 헤더가 있으면 subjectSharedFilter가
  // 활성 주제 1개로 제한해 새 주제가 목록에서 누락된다. 이 경우만 skipSubjectHeader로 헤더를 생략.
  if (workspaceId && !opts?.skipSubjectHeader) {
    headers["X-Laminar-Subject-Id"] = workspaceId;
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
