/**
 * TanStack Query hooks — 자주 쓰는 API 호출 wrapper.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, setCurrentWorkspaceId } from "./api";
import type {
  AdminBoardSummaryResponse,
  AdminCardBodyRevealResponse,
  AdminCardMetadataResponse,
  AttachmentParentType,
  AttachmentResponse,
  AuditLogResponse,
  AuthResponse,
  BoardGraphResponse,
  BoardResponse,
  CalendarViewResponse,
  CardRelationResponse,
  CardResponse,
  EquipmentResponse,
  EquipmentReservationResponse,
  GroupRelationResponse,
  GroupResponse,
  MemberResponse,
  PendingInvitationResponse,
  PerpetualColumnDefinitionResponse,
  PerpetualColumnType,
  PerpetualColumnValueResponse,
  PerpetualNoteResponse,
  PerpetualVersionResponse,
  RenderedBodyResponse,
  TabResponse,
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

/** 캘린더 드래그로 카드 일정 이동 (P2) — 임의 카드의 시작/종료일 PATCH. */
export function useRescheduleCard(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      cardId: string;
      startDate: string;
      endDate: string | null;
    }) =>
      api.patch<CardResponse>(`/api/cards/${input.cardId}`, {
        startDate: input.startDate,
        endDate: input.endDate,
      }),
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

export const perpetualKeys = {
  boardTabs: (boardId: string) => ["boards", boardId, "tabs"] as const,
  boardNotes: (boardId: string) =>
    ["boards", boardId, "perpetual-notes"] as const,
  boardColumns: (boardId: string) =>
    ["boards", boardId, "perpetual-columns"] as const,
  noteColumns: (noteId: string) =>
    ["perpetual-notes", noteId, "columns"] as const,
  noteVersions: (noteId: string) =>
    ["perpetual-notes", noteId, "versions"] as const,
};

export function useBoardTabs(boardId: string | null) {
  return useQuery<TabResponse[]>({
    queryKey: boardId ? perpetualKeys.boardTabs(boardId) : ["tabs", "noop"],
    queryFn: () => api.get<TabResponse[]>(`/api/boards/${boardId}/tabs`),
    enabled: Boolean(boardId),
  });
}

export function useCreateTab(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      name: string;
      parentTabId?: string | null;
      labelColor?: string | null;
    }) =>
      api.post<TabResponse>("/api/tabs", {
        boardId,
        parentTabId: input.parentTabId ?? null,
        name: input.name,
        visible: true,
        collapsed: false,
        showLabel: true,
        labelColor: input.labelColor ?? null,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: perpetualKeys.boardTabs(boardId) });
    },
  });
}

export function useDeleteTab(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (tabId: string) => api.delete<void>(`/api/tabs/${tabId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: perpetualKeys.boardTabs(boardId) });
    },
  });
}

export function useBoardPerpetualNotes(boardId: string | null) {
  return useQuery<PerpetualNoteResponse[]>({
    queryKey: boardId
      ? perpetualKeys.boardNotes(boardId)
      : ["perpetual-notes", "noop"],
    queryFn: () =>
      api.get<PerpetualNoteResponse[]>(
        `/api/boards/${boardId}/perpetual-notes`,
      ),
    enabled: Boolean(boardId),
  });
}

export function useCreatePerpetualNote(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      tabId: string;
      parentPerpetualId?: string | null;
      title: string;
      bodyMd?: string;
    }) =>
      api.post<PerpetualNoteResponse>("/api/perpetual-notes", {
        boardId,
        tabId: input.tabId,
        parentPerpetualId: input.parentPerpetualId ?? null,
        title: input.title,
        bodyMd: input.bodyMd ?? "",
        attrs: {},
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: perpetualKeys.boardNotes(boardId) });
    },
  });
}

export function useUpdatePerpetualNote(boardId: string, noteId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      title?: string;
      bodyMd?: string;
      parentPerpetualId?: string | null;
    }) =>
      api.patch<PerpetualNoteResponse>(`/api/perpetual-notes/${noteId}`, input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: perpetualKeys.boardNotes(boardId) });
      qc.invalidateQueries({ queryKey: perpetualKeys.noteVersions(noteId) });
    },
  });
}

export function useDeletePerpetualNote(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (noteId: string) =>
      api.delete<void>(`/api/perpetual-notes/${noteId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: perpetualKeys.boardNotes(boardId) });
    },
  });
}

export function useBoardPerpetualColumns(boardId: string | null) {
  return useQuery<PerpetualColumnDefinitionResponse[]>({
    queryKey: boardId
      ? perpetualKeys.boardColumns(boardId)
      : ["perpetual-columns", "noop"],
    queryFn: () =>
      api.get<PerpetualColumnDefinitionResponse[]>(
        `/api/boards/${boardId}/perpetual-column-definitions`,
      ),
    enabled: Boolean(boardId),
  });
}

export function useCreatePerpetualColumn(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      name: string;
      type: PerpetualColumnType;
      enumValues?: string[];
    }) =>
      api.post<PerpetualColumnDefinitionResponse>(
        "/api/perpetual-column-definitions",
        {
          boardId,
          name: input.name,
          type: input.type,
          enumValues: input.enumValues ?? null,
        },
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: perpetualKeys.boardColumns(boardId) });
    },
  });
}

export function useDeletePerpetualColumn(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (definitionId: string) =>
      api.delete<void>(`/api/perpetual-column-definitions/${definitionId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: perpetualKeys.boardColumns(boardId) });
    },
  });
}

export function useNoteColumns(noteId: string | null) {
  return useQuery<PerpetualColumnValueResponse[]>({
    queryKey: noteId
      ? perpetualKeys.noteColumns(noteId)
      : ["perpetual-notes", "noop", "columns"],
    queryFn: () =>
      api.get<PerpetualColumnValueResponse[]>(
        `/api/perpetual-notes/${noteId}/columns`,
      ),
    enabled: Boolean(noteId),
  });
}

export function useUpsertColumnValue(noteId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      columnDefinitionId: string;
      value: string | null;
    }) =>
      api.put<PerpetualColumnValueResponse>("/api/perpetual-column-values", {
        perpetualNoteId: noteId,
        columnDefinitionId: input.columnDefinitionId,
        value: input.value,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: perpetualKeys.noteColumns(noteId) });
    },
  });
}

export function useNoteVersions(noteId: string | null) {
  return useQuery<PerpetualVersionResponse[]>({
    queryKey: noteId
      ? perpetualKeys.noteVersions(noteId)
      : ["perpetual-notes", "noop", "versions"],
    queryFn: () =>
      api.get<PerpetualVersionResponse[]>(
        `/api/perpetual-notes/${noteId}/versions`,
      ),
    enabled: Boolean(noteId),
  });
}

export function useCommitVersion(noteId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      cardId?: string | null;
      summary?: string;
      bodyDiffMd?: string;
      markCurrent?: boolean;
    }) =>
      api.post<PerpetualVersionResponse>("/api/perpetual-versions", {
        perpetualNoteId: noteId,
        cardId: input.cardId ?? null,
        summary: input.summary ?? null,
        bodyDiffMd: input.bodyDiffMd ?? null,
        markCurrent: input.markCurrent ?? false,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: perpetualKeys.noteVersions(noteId) });
    },
  });
}

export function useMarkVersionCurrent(noteId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (versionId: string) =>
      api.post<PerpetualVersionResponse>(
        `/api/perpetual-versions/${versionId}/mark-current-diff`,
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: perpetualKeys.noteVersions(noteId) });
    },
  });
}

export const groupKeys = {
  byBoard: (boardId: string) => ["boards", boardId, "groups"] as const,
  members: (groupId: string) => ["groups", groupId, "cards"] as const,
  cardRelations: (boardId: string) =>
    ["boards", boardId, "card-relations"] as const,
  groupRelations: (boardId: string) =>
    ["boards", boardId, "group-relations"] as const,
};

export function useGroups(boardId: string | null) {
  return useQuery<GroupResponse[]>({
    queryKey: boardId ? groupKeys.byBoard(boardId) : ["groups", "noop"],
    queryFn: () => api.get<GroupResponse[]>(`/api/boards/${boardId}/groups`),
    enabled: Boolean(boardId),
  });
}

export function useCreateGroup(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { name: string; color?: string | null }) =>
      api.post<GroupResponse>("/api/groups", {
        boardId,
        name: input.name,
        color: input.color ?? null,
        attrs: {},
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: groupKeys.byBoard(boardId) });
      qc.invalidateQueries({ queryKey: ["boards", boardId, "graph"] });
    },
  });
}

export function useUpdateGroup(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      groupId,
      name,
      color,
    }: {
      groupId: string;
      name?: string;
      color?: string | null;
    }) =>
      api.patch<GroupResponse>(`/api/groups/${groupId}`, {
        name,
        color: color ?? null,
        attrs: undefined,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: groupKeys.byBoard(boardId) });
      qc.invalidateQueries({ queryKey: ["boards", boardId, "graph"] });
    },
  });
}

export function useDeleteGroup(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (groupId: string) =>
      api.delete<void>(`/api/groups/${groupId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: groupKeys.byBoard(boardId) });
      qc.invalidateQueries({ queryKey: ["boards", boardId, "graph"] });
    },
  });
}

export function useGroupMembers(groupId: string | null) {
  return useQuery<string[]>({
    queryKey: groupId ? groupKeys.members(groupId) : ["groups", "noop"],
    queryFn: () => api.get<string[]>(`/api/groups/${groupId}/cards`),
    enabled: Boolean(groupId),
  });
}

export function useAddGroupMember(boardId: string, groupId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (cardId: string) =>
      api.post<void>(`/api/groups/${groupId}/cards/${cardId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: groupKeys.members(groupId) });
      qc.invalidateQueries({ queryKey: ["boards", boardId, "graph"] });
    },
  });
}

export function useRemoveGroupMember(boardId: string, groupId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (cardId: string) =>
      api.delete<void>(`/api/groups/${groupId}/cards/${cardId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: groupKeys.members(groupId) });
      qc.invalidateQueries({ queryKey: ["boards", boardId, "graph"] });
    },
  });
}

export function useCardRelations(boardId: string | null) {
  return useQuery<CardRelationResponse[]>({
    queryKey: boardId
      ? groupKeys.cardRelations(boardId)
      : ["card-relations", "noop"],
    queryFn: () =>
      api.get<CardRelationResponse[]>(
        `/api/boards/${boardId}/card-relations`,
      ),
    enabled: Boolean(boardId),
  });
}

export function useCreateCardRelation(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      fromCardId: string;
      toCardId: string;
      relationKind?: string;
      summary?: string;
    }) =>
      api.post<CardRelationResponse>("/api/card-relations", {
        ...input,
        relationKind: input.relationKind ?? "RELATED",
        summary: input.summary ?? null,
        bodyMd: null,
        attrs: {},
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: groupKeys.cardRelations(boardId) });
      qc.invalidateQueries({ queryKey: ["boards", boardId, "graph"] });
    },
  });
}

export function useDeleteCardRelation(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (relationId: string) =>
      api.delete<void>(`/api/card-relations/${relationId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: groupKeys.cardRelations(boardId) });
      qc.invalidateQueries({ queryKey: ["boards", boardId, "graph"] });
    },
  });
}

export function useGroupRelations(boardId: string | null) {
  return useQuery<GroupRelationResponse[]>({
    queryKey: boardId
      ? groupKeys.groupRelations(boardId)
      : ["group-relations", "noop"],
    queryFn: () =>
      api.get<GroupRelationResponse[]>(
        `/api/boards/${boardId}/group-relations`,
      ),
    enabled: Boolean(boardId),
  });
}

export function useCreateGroupRelation(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      fromGroupId: string;
      toGroupId: string;
      relationKind?: string;
      summary?: string;
    }) =>
      api.post<GroupRelationResponse>("/api/group-relations", {
        ...input,
        relationKind: input.relationKind ?? "RELATED",
        summary: input.summary ?? null,
        bodyMd: null,
        attrs: {},
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: groupKeys.groupRelations(boardId) });
      qc.invalidateQueries({ queryKey: ["boards", boardId, "graph"] });
    },
  });
}

export function useDeleteGroupRelation(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (relationId: string) =>
      api.delete<void>(`/api/group-relations/${relationId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: groupKeys.groupRelations(boardId) });
      qc.invalidateQueries({ queryKey: ["boards", boardId, "graph"] });
    },
  });
}

export const equipmentKeys = {
  list: (activeOnly: boolean) => ["equipment", { activeOnly }] as const,
  byId: (equipmentId: string) => ["equipment", equipmentId] as const,
  reservations: (equipmentId: string, from: string, to: string) =>
    ["equipment", equipmentId, "reservations", from, to] as const,
  myReservations: ["me", "reservations"] as const,
};

export function useEquipmentList(activeOnly = true) {
  return useQuery<EquipmentResponse[]>({
    queryKey: equipmentKeys.list(activeOnly),
    queryFn: () =>
      api.get<EquipmentResponse[]>(`/api/equipment?activeOnly=${activeOnly}`),
  });
}

export function useEquipment(equipmentId: string | null) {
  return useQuery<EquipmentResponse>({
    queryKey: equipmentId
      ? equipmentKeys.byId(equipmentId)
      : ["equipment", "noop"],
    queryFn: () => api.get<EquipmentResponse>(`/api/equipment/${equipmentId}`),
    enabled: Boolean(equipmentId),
  });
}

export function useCreateEquipment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      name: string;
      description?: string;
      location?: string;
    }) =>
      api.post<EquipmentResponse>("/api/equipment", {
        ...input,
        defaultLogColumns: [],
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["equipment"] });
    },
  });
}

export function useUpdateEquipment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      equipmentId,
      ...input
    }: {
      equipmentId: string;
      name?: string;
      description?: string;
      location?: string;
    }) => api.patch<EquipmentResponse>(`/api/equipment/${equipmentId}`, input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["equipment"] });
    },
  });
}

export function useToggleEquipmentActive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      equipmentId,
      active,
    }: {
      equipmentId: string;
      active: boolean;
    }) =>
      api.post<EquipmentResponse>(
        `/api/equipment/${equipmentId}/toggle-active`,
        { active },
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["equipment"] });
    },
  });
}

export function useDeleteEquipment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (equipmentId: string) =>
      api.delete<void>(`/api/equipment/${equipmentId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["equipment"] });
    },
  });
}

export function useEquipmentReservations(
  equipmentId: string | null,
  from: string,
  to: string,
) {
  return useQuery<EquipmentReservationResponse[]>({
    queryKey: equipmentId
      ? equipmentKeys.reservations(equipmentId, from, to)
      : ["reservations", "noop"],
    queryFn: () =>
      api.get<EquipmentReservationResponse[]>(
        `/api/equipment/${equipmentId}/reservations?from=${encodeURIComponent(
          from,
        )}&to=${encodeURIComponent(to)}`,
      ),
    enabled: Boolean(equipmentId),
  });
}

export function useMyReservations() {
  return useQuery<EquipmentReservationResponse[]>({
    queryKey: equipmentKeys.myReservations,
    queryFn: () =>
      api.get<EquipmentReservationResponse[]>("/api/me/reservations"),
  });
}

export function useCreateReservation(equipmentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      startAt: string;
      endAt: string;
      purpose?: string;
      rrule?: string | null;
      cardId?: string | null;
    }) =>
      api.post<EquipmentReservationResponse>(
        `/api/equipment/${equipmentId}/reservations`,
        {
          startAt: input.startAt,
          endAt: input.endAt,
          purpose: input.purpose ?? null,
          rrule: input.rrule ?? null,
          cardId: input.cardId ?? null,
        },
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["equipment", equipmentId] });
      qc.invalidateQueries({ queryKey: equipmentKeys.myReservations });
    },
  });
}

export function useCancelReservation(equipmentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (reservationId: string) =>
      api.delete<void>(`/api/reservations/${reservationId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["equipment", equipmentId] });
      qc.invalidateQueries({ queryKey: equipmentKeys.myReservations });
    },
  });
}

export const adminKeys = {
  allBoards: ["admin", "boards"] as const,
  boardCards: (boardId: string) =>
    ["admin", "boards", boardId, "cards"] as const,
  auditLogs: ["audit-logs"] as const,
};

export function useAdminAllBoards() {
  return useQuery<AdminBoardSummaryResponse[]>({
    queryKey: adminKeys.allBoards,
    queryFn: () =>
      api.get<AdminBoardSummaryResponse[]>("/api/admin/boards"),
  });
}

export function useAdminBoardCards(boardId: string | null) {
  return useQuery<AdminCardMetadataResponse[]>({
    queryKey: boardId
      ? adminKeys.boardCards(boardId)
      : ["admin", "boards", "noop"],
    queryFn: () =>
      api.get<AdminCardMetadataResponse[]>(
        `/api/admin/boards/${boardId}/cards`,
      ),
    enabled: Boolean(boardId),
  });
}

export function useRevealCardBody() {
  return useMutation({
    mutationFn: ({ cardId, reason }: { cardId: string; reason: string }) =>
      api.post<AdminCardBodyRevealResponse>(
        `/api/admin/cards/${cardId}/reveal-body`,
        { reason },
      ),
  });
}

export function useAuditLogs(limit = 100) {
  return useQuery<AuditLogResponse[]>({
    queryKey: [...adminKeys.auditLogs, limit] as const,
    queryFn: () =>
      api.get<AuditLogResponse[]>(`/api/audit-logs?limit=${limit}`),
  });
}
