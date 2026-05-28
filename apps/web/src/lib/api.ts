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

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  const workspaceId = getCurrentWorkspaceId();
  if (workspaceId) {
    headers["X-Laminar-Workspace-Id"] = workspaceId;
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    credentials: "include",
    body: body === undefined ? undefined : JSON.stringify(body),
  });

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
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, body),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  delete: <T>(path: string) => request<T>("DELETE", path),
};
