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
}

export interface Tab {
  id: Uuid;
  name: string;
  slug: string;
  priority: number;
}

export interface Card {
  id: Uuid;
  tabId: Uuid | null;
  title: string;
  bodyMd: string | null;
  startDate: IsoDate | null;
  endDate: IsoDate | null;
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

export interface TabGraph {
  tabId: Uuid;
  cards: Card[];
  cardRelations: CardRelation[];
}

const graphKey = (tabId: string) => ["tabGraph", tabId] as const;

function invalidateGraph(qc: QueryClient, tabId: string): Promise<void> {
  return qc.invalidateQueries({ queryKey: graphKey(tabId) });
}

export function useSubjects() {
  return useQuery({
    queryKey: ["subjects"],
    queryFn: () => api.get<Subject[]>("/api/subjects"),
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
    }) => {
      const { cardId, ...patch } = input;
      return api.patch<Card>(`/api/cards/${cardId}`, patch);
    },
    // 연쇄 이동이 다른 카드 날짜를 바꿀 수 있으므로 성공/실패 모두 그래프 재조회(서버 진실로 정렬).
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

function slugify(name: string): string {
  const base = name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  const suffix = Math.random().toString(36).slice(2, 8);
  return base ? `${base}-${suffix}` : `tab-${suffix}`;
}
