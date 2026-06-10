/**
 * 인증 TanStack Query 훅 — 레거시 lib/queries.ts에서 추출 (DAG 개편 Phase 6 정리).
 * /api/auth/me·login·signup만 다룬다. 워크스페이스/카드 등 데이터는 리소스별 lib 모듈(subjects·tabs·graph·cards·groups·categories).
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api";

export interface AuthResponse {
  userId: string;
  email: string;
  displayName: string;
  emailVerified: boolean;
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
        return await api.get<AuthResponse>("/api/auth/me");
      } catch {
        return null;
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
    },
  });
}

export function useLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: LoginInput) => api.post<AuthResponse>("/api/auth/login", input),
    onSuccess: (data) => {
      qc.setQueryData(ME_KEY, data);
    },
  });
}
