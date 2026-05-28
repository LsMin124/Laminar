/**
 * TanStack Query hooks — 자주 쓰는 API 호출 wrapper.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, setCurrentWorkspaceId } from "./api";
import type {
  AttachmentParentType,
  AttachmentResponse,
  AuthResponse,
  BoardGraphResponse,
  BoardResponse,
  CalendarViewResponse,
  CardResponse,
  MemberResponse,
  PendingInvitationResponse,
  RenderedBodyResponse,
  WorkspaceResponse,
  WorkspaceRole,
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
  boardCalendar: (boardId: string, from: string, to: string) =>
    ["boards", boardId, "calendar", from, to] as const,
  card: (cardId: string) => ["cards", cardId] as const,
  cardRendered: (cardId: string) => ["cards", cardId, "rendered"] as const,
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

export function useBoard(boardId: string | null) {
  return useQuery<BoardResponse>({
    queryKey: boardId ? queryKeys.board(boardId) : ["boards", "noop"],
    queryFn: () => api.get<BoardResponse>(`/api/boards/${boardId}`),
    enabled: Boolean(boardId),
  });
}

export function useBoardCalendar(
  boardId: string | null,
  from: string,
  to: string,
) {
  return useQuery<CalendarViewResponse>({
    queryKey: boardId
      ? queryKeys.boardCalendar(boardId, from, to)
      : ["boards", "noop"],
    queryFn: () =>
      api.get<CalendarViewResponse>(
        `/api/boards/${boardId}/calendar?from=${from}&to=${to}`,
      ),
    enabled: Boolean(boardId),
  });
}

export function useCard(cardId: string | null) {
  return useQuery<CardResponse>({
    queryKey: cardId ? queryKeys.card(cardId) : ["cards", "noop"],
    queryFn: () => api.get<CardResponse>(`/api/cards/${cardId}`),
    enabled: Boolean(cardId),
  });
}

export function useCardRendered(cardId: string | null) {
  return useQuery<RenderedBodyResponse>({
    queryKey: cardId ? queryKeys.cardRendered(cardId) : ["cards", "noop"],
    queryFn: () =>
      api.get<RenderedBodyResponse>(`/api/cards/${cardId}/rendered`),
    enabled: Boolean(cardId),
  });
}

export interface CreateCardInput {
  boardId: string;
  title: string;
  slug?: string;
  bodyMd?: string;
  startDate?: string | null;
  endDate?: string | null;
  startTime?: string | null;
  allDay?: boolean;
  timeZone?: string | null;
  importance?: string;
  linkedPerpetualId?: string | null;
  rrule?: string | null;
  attrs?: Record<string, unknown>;
}

export function useCreateCard(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateCardInput) =>
      api.post<CardResponse>("/api/cards", { ...input, boardId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["boards", boardId] });
    },
  });
}

export interface UpdateCardInput {
  title?: string;
  bodyMd?: string;
  startDate?: string | null;
  endDate?: string | null;
  startTime?: string | null;
  allDay?: boolean;
  timeZone?: string | null;
  importance?: string;
  completed?: boolean;
  linkedPerpetualId?: string | null;
  rrule?: string | null;
  attrs?: Record<string, unknown>;
}

export function useUpdateCard(cardId: string, boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: UpdateCardInput) =>
      api.patch<CardResponse>(`/api/cards/${cardId}`, input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.card(cardId) });
      qc.invalidateQueries({ queryKey: queryKeys.cardRendered(cardId) });
      qc.invalidateQueries({ queryKey: ["boards", boardId] });
    },
  });
}

export function useDeleteCard(cardId: string, boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.delete<void>(`/api/cards/${cardId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["boards", boardId] });
    },
  });
}

export const attachmentKeys = {
  byParent: (parentType: AttachmentParentType, parentId: string) =>
    ["attachments", parentType, parentId] as const,
  downloadUrl: (attachmentId: string) =>
    ["attachments", attachmentId, "download-url"] as const,
};

export function useAttachmentsByParent(
  parentType: AttachmentParentType,
  parentId: string | null,
) {
  return useQuery<AttachmentResponse[]>({
    queryKey: parentId
      ? attachmentKeys.byParent(parentType, parentId)
      : ["attachments", "noop"],
    queryFn: () =>
      api.get<AttachmentResponse[]>(
        `/api/attachments?parentType=${parentType}&parentId=${parentId}`,
      ),
    enabled: Boolean(parentId),
  });
}

export function useDeleteAttachment(
  parentType: AttachmentParentType,
  parentId: string,
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (attachmentId: string) =>
      api.delete<void>(`/api/attachments/${attachmentId}`),
    onSuccess: () => {
      qc.invalidateQueries({
        queryKey: attachmentKeys.byParent(parentType, parentId),
      });
    },
  });
}

export function useBoardGraph(boardId: string | null) {
  return useQuery<BoardGraphResponse>({
    queryKey: boardId ? ["boards", boardId, "graph"] : ["boards", "noop"],
    queryFn: () =>
      api.get<BoardGraphResponse>(`/api/boards/${boardId}/graph`),
    enabled: Boolean(boardId),
  });
}

export const memberKeys = {
  list: ["members"] as const,
  pending: ["invitations", "pending"] as const,
};

export function useMembers() {
  return useQuery<MemberResponse[]>({
    queryKey: memberKeys.list,
    queryFn: () =>
      api.get<MemberResponse[]>("/api/workspaces/current/members"),
  });
}

export function useUpdateMemberRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      userId,
      role,
    }: {
      userId: string;
      role: WorkspaceRole;
    }) =>
      api.patch<MemberResponse>(
        `/api/workspaces/current/members/${userId}/role`,
        { role },
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: memberKeys.list });
    },
  });
}

export function useRemoveMember() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) =>
      api.delete<void>(`/api/workspaces/current/members/${userId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: memberKeys.list });
    },
  });
}

export function usePendingInvitations() {
  return useQuery<PendingInvitationResponse[]>({
    queryKey: memberKeys.pending,
    queryFn: () =>
      api.get<PendingInvitationResponse[]>(
        "/api/workspaces/current/invitations",
      ),
  });
}

export function useRevokeInvitation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (invitationId: string) =>
      api.delete<void>(
        `/api/workspaces/current/invitations/${invitationId}`,
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: memberKeys.pending });
    },
  });
}
