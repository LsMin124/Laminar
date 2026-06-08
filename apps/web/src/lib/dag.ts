/**
 * DAG 개편 Phase 4 — 카드 DAG 캔버스 데이터 레이어 (새 contract: subject/tab/canvasY).
 *
 * 레거시 lib/queries.ts와 분리된 신규 표면. 백엔드 Phase 1~3 contract(/api/tabs·/api/subjects,
 * subjectId/tabId, cards.canvasY, 시간강제·연쇄이동)에 정합한다.
 */
import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from "@tanstack/react-query";
import { api } from "./api";

export type Uuid = string;
export type IsoDate = string;

export interface Subject {
  id: Uuid;
  name: string;
  slug: string;
  /** 주제(워크스페이스) 전반 개요 — 독립 마크다운 문서. */
  bodyMd: string | null;
}

export interface Tab {
  id: Uuid;
  name: string;
  slug: string;
  priority: number;
  /** 탭(보드)별 메모/개요 — 독립 마크다운 문서. */
  bodyMd: string | null;
}

export interface Card {
  id: Uuid;
  tabId: Uuid | null;
  title: string;
  bodyMd: string | null;
  startDate: IsoDate | null;
  endDate: IsoDate | null;
  startTime: string | null;
  allDay: boolean;
  importance: string;
  completed: boolean;
  priority: number;
  canvasY: number | null;
}

export interface CardRelation {
  id: Uuid;
  fromCardId: Uuid;
  toCardId: Uuid;
  relationKind: string;
  summary: string | null;
}

export interface Group {
  id: Uuid;
  name: string;
  color: string | null;
  /** 그룹도 카드처럼 독립 마크다운 문서를 가진다(서브그래프 목표·메모). */
  bodyMd: string | null;
}

/** 그룹 간 화살표 — 카드 관계(CardRelation)와 별개 레이어. 캔버스에서 쿨 톤·점선으로 구분 렌더. */
export interface GroupRelation {
  id: Uuid;
  fromGroupId: Uuid;
  toGroupId: Uuid;
  relationKind: string;
  summary: string | null;
}

/** 카드 카테고리 — 주제(subject) 단위로 공유되는 명명 분류(이름 + 색). 카드 좌측 스트라이프에 반영. */
export interface Category {
  id: Uuid;
  name: string;
  color: string | null;
}

export interface TabGraph {
  tabId: Uuid;
  cards: Card[];
  cardRelations: CardRelation[];
  groups: Group[];
  /** 그룹 간 화살표 목록. */
  groupRelations: GroupRelation[];
  /** groupId → 멤버 cardId 목록. */
  groupMembers: Record<string, string[]>;
  /** 현재 주제의 카테고리 목록(모든 탭 공유). */
  categories: Category[];
  /** cardId → categoryId. 미분류 카드는 키 없음. */
  cardCategoryIds: Record<string, string>;
}

const graphKey = (tabId: string) => ["tabGraph", tabId] as const;

function invalidateGraph(qc: QueryClient, tabId: string): Promise<void> {
  return qc.invalidateQueries({ queryKey: graphKey(tabId) });
}

export function useSubjects() {
  return useQuery({
    queryKey: ["subjects"],
    // 헤더 생략 → SYSTEM scope로 내 전체 주제 조회(헤더가 있으면 subjectSharedFilter가 활성 주제 1개로 제한).
    queryFn: () => api.get<Subject[]>("/api/subjects", { skipSubjectHeader: true }),
  });
}

export function useCreateSubject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (name: string) =>
      api.post<Subject>("/api/subjects", {
        name,
        slug: slugify(name),
        defaultTimezone: "Asia/Seoul",
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["subjects"] }),
  });
}

/**
 * 현재(활성) 주제 수정(이름·본문) — 백엔드 PATCH /current는 헤더의 활성 주제를 대상으로 한다.
 * `id`는 본문 자동저장 낙관적 반영(["subjects"] 캐시 행 갱신)용으로만 쓰이고 요청 본문엔 보내지 않는다.
 */
export function useUpdateSubject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ name, bodyMd }: { id?: string; name?: string; bodyMd?: string | null }) =>
      api.patch<Subject>("/api/subjects/current", { name, bodyMd }),
    onMutate: async ({ id, name, bodyMd }) => {
      const prev = qc.getQueryData<Subject[]>(["subjects"]);
      if (id) {
        qc.setQueryData<Subject[]>(["subjects"], (list) =>
          list?.map((s) =>
            s.id === id
              ? {
                  ...s,
                  ...(name !== undefined ? { name } : {}),
                  ...(bodyMd !== undefined ? { bodyMd } : {}),
                }
              : s,
          ),
        );
      }
      await qc.cancelQueries({ queryKey: ["subjects"] });
      return { prev };
    },
    onError: (_err, _input, ctx) => {
      const snap = ctx as { prev?: Subject[] } | undefined;
      if (snap?.prev) qc.setQueryData(["subjects"], snap.prev);
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ["subjects"] }),
  });
}

/** 탭(보드) 수정(이름·본문) — 본문 자동저장이 깜빡이지 않도록 ["tabs"] 캐시 낙관적 반영. */
export function useUpdateTab() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ tabId, name, bodyMd }: { tabId: string; name?: string; bodyMd?: string | null }) =>
      api.patch<Tab>(`/api/tabs/${tabId}`, { name, bodyMd }),
    onMutate: async ({ tabId, name, bodyMd }) => {
      const prev = qc.getQueryData<Tab[]>(["tabs"]);
      qc.setQueryData<Tab[]>(["tabs"], (list) =>
        list?.map((t) =>
          t.id === tabId
            ? {
                ...t,
                ...(name !== undefined ? { name } : {}),
                ...(bodyMd !== undefined ? { bodyMd } : {}),
              }
            : t,
        ),
      );
      await qc.cancelQueries({ queryKey: ["tabs"] });
      return { prev };
    },
    onError: (_err, _input, ctx) => {
      const snap = ctx as { prev?: Tab[] } | undefined;
      if (snap?.prev) qc.setQueryData(["tabs"], snap.prev);
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ["tabs"] }),
  });
}

/**
 * 현재(활성) 주제 삭제 — 자식(탭·카드·관계·그룹) 전부 영구 삭제(백엔드 FK CASCADE).
 * 헤더 초기화·활성 주제 재선정·캐시 무효화는 호출부(SubjectLayout)가 순서대로 처리한다.
 */
export function useDeleteSubject() {
  return useMutation({
    mutationFn: () => api.delete<void>("/api/subjects/current"),
  });
}

export function useTabs() {
  return useQuery({
    queryKey: ["tabs"],
    queryFn: () => api.get<Tab[]>("/api/tabs"),
  });
}

export function useCreateTab() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (name: string) =>
      api.post<Tab>("/api/tabs", { name, slug: slugify(name) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["tabs"] }),
  });
}

export function useTabGraph(tabId: string | null) {
  return useQuery({
    queryKey: graphKey(tabId ?? ""),
    queryFn: () => api.get<TabGraph>(`/api/tabs/${tabId}/graph`),
    enabled: !!tabId,
  });
}

/** 카드 단건 조회 — 예약↔카드 연결 등 크로스 표면에서 cardId→제목 해석용(없으면 404). */
export function useCardById(cardId: string | null) {
  return useQuery({
    queryKey: ["card", cardId],
    queryFn: () => api.get<Card>(`/api/cards/${cardId}`),
    enabled: !!cardId,
    retry: false,
  });
}

export function useCreateCard(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { title: string; startDate: IsoDate | null }) =>
      api.post<Card>("/api/cards", {
        tabId,
        title: input.title,
        startDate: input.startDate,
        allDay: true,
      }),
    onSettled: () => invalidateGraph(qc, tabId),
  });
}

export function useUpdateCard(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      cardId: string;
      startDate?: IsoDate | null;
      canvasY?: number | null;
      title?: string;
      completed?: boolean;
      startTime?: string | null;
      allDay?: boolean;
      bodyMd?: string | null;
    }) => {
      const { cardId, ...patch } = input;
      return api.patch<Card>(`/api/cards/${cardId}`, patch);
    },
    // 제목/완료 토글이 즉시 반영되도록 낙관적 업데이트.
    onMutate: async (input) => {
      const prev = qc.getQueryData<TabGraph>(graphKey(tabId));
      qc.setQueryData<TabGraph>(graphKey(tabId), (g) =>
        g
          ? {
              ...g,
              cards: g.cards.map((c) =>
                c.id === input.cardId
                  ? {
                      ...c,
                      ...(input.title !== undefined ? { title: input.title } : {}),
                      ...(input.completed !== undefined ? { completed: input.completed } : {}),
                      ...(input.startDate !== undefined ? { startDate: input.startDate } : {}),
                      ...(input.canvasY !== undefined ? { canvasY: input.canvasY } : {}),
                      ...(input.startTime !== undefined ? { startTime: input.startTime } : {}),
                      ...(input.allDay !== undefined ? { allDay: input.allDay } : {}),
                      ...(input.bodyMd !== undefined ? { bodyMd: input.bodyMd } : {}),
                    }
                  : c,
              ),
            }
          : g,
      );
      await qc.cancelQueries({ queryKey: graphKey(tabId) });
      return { prev };
    },
    onError: (_err, _input, ctx) => {
      const snapshot = ctx as { prev?: TabGraph } | undefined;
      if (snapshot?.prev) qc.setQueryData(graphKey(tabId), snapshot.prev);
    },
    onSettled: () => invalidateGraph(qc, tabId),
  });
}

/**
 * 카드 geometry 이동/리사이즈 — 이전 일자 이동으로 위반되는 선행 엣지(severRelationIds)를 먼저 삭제한 뒤
 * startDate/endDate/canvasY를 patch. 삭제+patch를 한 mutation으로 묶어 그래프는 1회만 재조회한다.
 * 정의된 필드만 전송(undefined는 생략 → 백엔드 무변경).
 */
export function useMoveCard(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: {
      cardId: string;
      startDate?: IsoDate | null;
      endDate?: IsoDate | null;
      canvasY?: number | null;
      severRelationIds?: string[];
    }) => {
      for (const id of input.severRelationIds ?? []) {
        await api.delete<void>(`/api/card-relations/${id}`);
      }
      const patch: Record<string, unknown> = {};
      if (input.startDate !== undefined) patch.startDate = input.startDate;
      if (input.endDate !== undefined) patch.endDate = input.endDate;
      if (input.canvasY !== undefined) patch.canvasY = input.canvasY;
      return api.patch<Card>(`/api/cards/${input.cardId}`, patch);
    },
    // 낙관적 업데이트 — 드롭 즉시 화면에 반영해 "놓은 자리로 안 가고 원위치로 되돌아갔다 점프"하는 깜빡임 제거.
    onMutate: async (input) => {
      const prev = qc.getQueryData<TabGraph>(graphKey(tabId));
      qc.setQueryData<TabGraph>(graphKey(tabId), (g) =>
        g
          ? {
              ...g,
              cards: g.cards.map((c) =>
                c.id === input.cardId
                  ? {
                      ...c,
                      ...(input.startDate !== undefined ? { startDate: input.startDate } : {}),
                      ...(input.endDate !== undefined ? { endDate: input.endDate } : {}),
                      ...(input.canvasY !== undefined ? { canvasY: input.canvasY } : {}),
                    }
                  : c,
              ),
              cardRelations: g.cardRelations.filter(
                (r) => !(input.severRelationIds ?? []).includes(r.id),
              ),
            }
          : g,
      );
      await qc.cancelQueries({ queryKey: graphKey(tabId) });
      return { prev };
    },
    onError: (_err, _input, ctx) => {
      const snapshot = ctx as { prev?: TabGraph } | undefined;
      if (snapshot?.prev) qc.setQueryData(graphKey(tabId), snapshot.prev);
    },
    onSettled: () => invalidateGraph(qc, tabId),
  });
}

export function useDeleteCard(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (cardId: string) => api.delete<void>(`/api/cards/${cardId}`),
    onSettled: () => invalidateGraph(qc, tabId),
  });
}

export function useCreateRelation(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { fromCardId: string; toCardId: string }) =>
      api.post<CardRelation>("/api/card-relations", {
        fromCardId: input.fromCardId,
        toCardId: input.toCardId,
      }),
    onSettled: () => invalidateGraph(qc, tabId),
  });
}

export function useDeleteRelation(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (relationId: string) =>
      api.delete<void>(`/api/card-relations/${relationId}`),
    onSettled: () => invalidateGraph(qc, tabId),
  });
}

export function useCreateGroup(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { name: string; color?: string | null }) =>
      api.post<Group>("/api/groups", {
        tabId,
        name: input.name,
        color: input.color ?? null,
      }),
    onSettled: () => invalidateGraph(qc, tabId),
  });
}

export function useDeleteGroup(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (groupId: string) => api.delete<void>(`/api/groups/${groupId}`),
    onSettled: () => invalidateGraph(qc, tabId),
  });
}

/** 그룹 속성(이름·색·본문) 수정 — 본문 자동저장이 깜빡이지 않도록 낙관적 반영. */
export function useUpdateGroup(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      groupId: string;
      name?: string;
      color?: string | null;
      bodyMd?: string | null;
    }) => {
      const { groupId, ...patch } = input;
      return api.patch<Group>(`/api/groups/${groupId}`, patch);
    },
    onMutate: async (input) => {
      const prev = qc.getQueryData<TabGraph>(graphKey(tabId));
      qc.setQueryData<TabGraph>(graphKey(tabId), (g) =>
        g
          ? {
              ...g,
              groups: g.groups.map((grp) =>
                grp.id === input.groupId
                  ? {
                      ...grp,
                      ...(input.name !== undefined ? { name: input.name } : {}),
                      ...(input.color !== undefined ? { color: input.color } : {}),
                      ...(input.bodyMd !== undefined ? { bodyMd: input.bodyMd } : {}),
                    }
                  : grp,
              ),
            }
          : g,
      );
      await qc.cancelQueries({ queryKey: graphKey(tabId) });
      return { prev };
    },
    onError: (_err, _input, ctx) => {
      const snapshot = ctx as { prev?: TabGraph } | undefined;
      if (snapshot?.prev) qc.setQueryData(graphKey(tabId), snapshot.prev);
    },
    onSettled: () => invalidateGraph(qc, tabId),
  });
}

/** 그룹 간 화살표 생성 — 같은 탭의 두 그룹만(백엔드 검증). 사이클 강제는 없음(카드와 달리 시간축 무관). */
export function useCreateGroupRelation(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { fromGroupId: string; toGroupId: string }) =>
      api.post<GroupRelation>("/api/group-relations", {
        fromGroupId: input.fromGroupId,
        toGroupId: input.toGroupId,
      }),
    onSettled: () => invalidateGraph(qc, tabId),
  });
}

export function useDeleteGroupRelation(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (relationId: string) =>
      api.delete<void>(`/api/group-relations/${relationId}`),
    onSettled: () => invalidateGraph(qc, tabId),
  });
}

export function useAddCardToGroup(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { groupId: string; cardId: string }) =>
      api.post<void>(`/api/groups/${input.groupId}/cards/${input.cardId}`),
    onSettled: () => invalidateGraph(qc, tabId),
  });
}

export function useRemoveCardFromGroup(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { groupId: string; cardId: string }) =>
      api.delete<void>(`/api/groups/${input.groupId}/cards/${input.cardId}`),
    onSettled: () => invalidateGraph(qc, tabId),
  });
}

// ── 카드 카테고리(주제 단위 명명 분류) ──────────────────────────────────
// 카테고리 목록·카드↔카테고리 매핑은 TabGraph 응답에 함께 실려 오므로, 변경 시 그래프를 무효화해 반영한다.
// 카테고리는 주제 전역(전 탭 공유)이라 생성/수정/삭제는 모든 탭 그래프(prefix ["tabGraph"])를 무효화한다.

/** 카드에 카테고리 지정/해제 — categoryId null이면 미분류. 스트라이프 색이 즉시 바뀌도록 낙관적. */
export function useSetCardCategory(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { cardId: string; categoryId: string | null }) =>
      api.put<void>(`/api/cards/${input.cardId}/category`, {
        categoryId: input.categoryId,
      }),
    onMutate: async (input) => {
      const prev = qc.getQueryData<TabGraph>(graphKey(tabId));
      qc.setQueryData<TabGraph>(graphKey(tabId), (g) => {
        if (!g) return g;
        const next = { ...g.cardCategoryIds };
        if (input.categoryId) next[input.cardId] = input.categoryId;
        else delete next[input.cardId];
        return { ...g, cardCategoryIds: next };
      });
      await qc.cancelQueries({ queryKey: graphKey(tabId) });
      return { prev };
    },
    onError: (_err, _input, ctx) => {
      const snapshot = ctx as { prev?: TabGraph } | undefined;
      if (snapshot?.prev) qc.setQueryData(graphKey(tabId), snapshot.prev);
    },
    onSettled: () => invalidateGraph(qc, tabId),
  });
}

export function useCreateCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { name: string; color: string | null }) =>
      api.post<Category>("/api/categories", input),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["tabGraph"] }),
  });
}

export function useUpdateCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { id: string; name?: string; color?: string | null }) => {
      const { id, ...patch } = input;
      return api.patch<Category>(`/api/categories/${id}`, patch);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["tabGraph"] }),
  });
}

export function useDeleteCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`/api/categories/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["tabGraph"] }),
  });
}

function slugify(name: string): string {
  const base = name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  const suffix = Math.random().toString(36).slice(2, 8);
  return base ? `${base}-${suffix}` : `tab-${suffix}`;
}
