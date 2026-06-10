/** 카드 + 카드 관계(화살표) 데이터 훅 — lib/dag.ts 리소스별 분리 (DX-2). */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api";
import { dagKeys, invalidateGraph } from "./dagKeys";
import type { Card, CardRelation, IsoDate, TabGraph } from "./graphTypes";
import { optimisticUpdate, rollbackTo } from "./optimistic";

/** 카드 단건 조회 — 예약↔카드 연결 등 크로스 표면에서 cardId→제목 해석용(없으면 404). */
export function useCardById(cardId: string | null) {
  return useQuery({
    queryKey: dagKeys.card(cardId),
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
    onMutate: (input) =>
      optimisticUpdate<TabGraph>(qc, dagKeys.tabGraph(tabId), (g) => ({
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
              }
            : c,
        ),
      })),
    // 본문(bodyMd)은 그래프 페이로드에서 빠져 단건 캐시(useCardById)에 산다 — PATCH 응답으로 단건
    // 캐시를 갱신해 본문 편집 후 '완료' 시 옛 본문으로 되돌아가지 않게 한다.
    onSuccess: (data, input) => qc.setQueryData(dagKeys.card(input.cardId), data),
    onError: rollbackTo<TabGraph>(qc, dagKeys.tabGraph(tabId)),
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
    onMutate: (input) =>
      optimisticUpdate<TabGraph>(qc, dagKeys.tabGraph(tabId), (g) => ({
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
      })),
    onError: rollbackTo<TabGraph>(qc, dagKeys.tabGraph(tabId)),
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

/** 엣지 라벨(summary) 수정 — 라벨이 곧 화살표가 나타내는 관계. 깜빡임 없이 낙관적 반영. */
export function useUpdateRelation(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { relationId: string; summary: string | null }) =>
      api.patch<CardRelation>(`/api/card-relations/${input.relationId}`, {
        summary: input.summary,
      }),
    onMutate: (input) =>
      optimisticUpdate<TabGraph>(qc, dagKeys.tabGraph(tabId), (g) => ({
        ...g,
        cardRelations: g.cardRelations.map((r) =>
          r.id === input.relationId ? { ...r, summary: input.summary } : r,
        ),
      })),
    onError: rollbackTo<TabGraph>(qc, dagKeys.tabGraph(tabId)),
    onSettled: () => invalidateGraph(qc, tabId),
  });
}
