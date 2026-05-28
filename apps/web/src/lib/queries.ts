/**
 * TanStack Query hooks — 자주 쓰는 API 호출 wrapper.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, setCurrentWorkspaceId } from "./api";
import type {
  AuthResponse,
  BoardResponse,
  CardResponse,
  WorkspaceResponse,
} from "./types";

export interface SignupInput {
  email: string;
  password: string;
  displayName: string;
}

export interface LoginInput {
  email: string;
  password: string;
}

export const queryKeys = {
  me: ["auth", "me"] as const,
  currentWorkspace: ["workspaces", "current"] as const,
  boards: ["boards"] as const,
  board: (boardId: string) => ["boards", boardId] as const,
  boardCards: (boardId: string) => ["boards", boardId, "cards"] as const,
};

export function useMe() {
  return useQuery<AuthResponse | null>({
    queryKey: queryKeys.me,
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
    mutationFn: (input: SignupInput) =>
      api.post<AuthResponse>("/api/auth/signup", input),
    onSuccess: (data) => {
      qc.setQueryData(queryKeys.me, data);
    },
  });
}

export function useLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: LoginInput) =>
      api.post<AuthResponse>("/api/auth/login", input),
    onSuccess: (data) => {
      qc.setQueryData(queryKeys.me, data);
    },
  });
}

export function useLogout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<void>("/api/auth/logout"),
    onSuccess: () => {
      setCurrentWorkspaceId(null);
      qc.clear();
    },
  });
}

export function useCurrentWorkspace(enabled: boolean) {
  return useQuery<WorkspaceResponse>({
    queryKey: queryKeys.currentWorkspace,
    queryFn: () => api.get<WorkspaceResponse>("/api/workspaces/current"),
    enabled,
    staleTime: 60_000,
  });
}

export function useBoards(enabled: boolean) {
  return useQuery<BoardResponse[]>({
    queryKey: queryKeys.boards,
    queryFn: () => api.get<BoardResponse[]>("/api/boards"),
    enabled,
  });
}

export interface CreateBoardInput {
  name: string;
  slug: string;
  defaultView?: "CALENDAR" | "LIST";
}

export function useCreateBoard() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateBoardInput) =>
      api.post<BoardResponse>("/api/boards", input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.boards });
    },
  });
}

export function useBoardCards(boardId: string | null) {
  return useQuery<CardResponse[]>({
    queryKey: boardId ? queryKeys.boardCards(boardId) : ["boards", "noop"],
    queryFn: () =>
      api.get<CardResponse[]>(`/api/boards/${boardId}/cards`),
    enabled: Boolean(boardId),
  });
}
