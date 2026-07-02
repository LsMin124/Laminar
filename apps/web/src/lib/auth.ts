/**
 * 인증 TanStack Query 훅 — 레거시 lib/queries.ts에서 추출 (DAG 개편 Phase 6 정리).
 * /api/auth/me·login·signup만 다룬다. 워크스페이스/카드 등 데이터는 리소스별 lib 모듈(subjects·tabs·graph·cards·groups·categories).
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "./api";
import { markAuthenticated } from "./silentRefresh";

export interface AuthResponse {
  userId: string;
  email: string;
  displayName: string;
  emailVerified: boolean;
  /** access 토큰 TTL(초) — 서버 설정(app.jwt.access-ttl)이 정본. 선제 silent refresh 타이머 기준(G1). */
  accessTtlSeconds: number;
}

export interface SignupInput {
  email: string;
  password: string;
  displayName: string;
}

export interface LoginInput {
  email: string;
  password: string;
}

const ME_KEY = ["auth", "me"] as const;

export function useMe() {
  return useQuery<AuthResponse | null>({
    queryKey: ME_KEY,
    queryFn: async () => {
      try {
        const me = await api.get<AuthResponse>("/api/auth/me");
        // me는 토큰 발급 시점이 아니라 TTL 전체 재무장이 과대평가일 수 있음 — 빗나가면 반응적 401 경로가 보정.
        markAuthenticated(me.accessTtlSeconds);
        return me;
      } catch (err) {
        // 401(미인증)만 "비로그인"으로 확정 — 일시 500·네트워크 오류는 전파해 강제 로그아웃을 막는다(Q5).
        if (err instanceof ApiError && err.status === 401) {
          return null;
        }
        throw err;
      }
    },
    staleTime: 60_000,
  });
}

export function useSignup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: SignupInput) => api.post<AuthResponse>("/api/auth/signup", input),
    onSuccess: (data) => {
      qc.setQueryData(ME_KEY, data);
      markAuthenticated(data.accessTtlSeconds);
    },
  });
}

export function useLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: LoginInput) => api.post<AuthResponse>("/api/auth/login", input),
    onSuccess: (data) => {
      qc.setQueryData(ME_KEY, data);
      markAuthenticated(data.accessTtlSeconds);
    },
  });
}
