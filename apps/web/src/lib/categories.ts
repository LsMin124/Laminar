/**
 * 카드 카테고리(주제 단위 명명 분류) 데이터 훅 — lib/dag.ts 리소스별 분리 (DX-2).
 *
 * 카테고리 목록·카드↔카테고리 매핑은 TabGraph 응답에 함께 실려 오므로, 변경 시 그래프를 무효화해 반영한다.
 * 카테고리는 주제 전역(전 탭 공유)이라 생성/수정/삭제는 모든 탭 그래프(prefix dagKeys.tabGraphs)를 무효화한다.
 */
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "./api";
import { dagKeys, invalidateGraph } from "./dagKeys";
import type { Category, TabGraph } from "./graphTypes";
import { optimisticUpdate, rollbackTo } from "./optimistic";

/** 카드에 카테고리 지정/해제 — categoryId null이면 미분류. 스트라이프 색이 즉시 바뀌도록 낙관적. */
export function useSetCardCategory(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { cardId: string; categoryId: string | null }) =>
      api.put<void>(`/api/cards/${input.cardId}/category`, {
        categoryId: input.categoryId,
      }),
    onMutate: (input) =>
      optimisticUpdate<TabGraph>(qc, dagKeys.tabGraph(tabId), (g) => {
        const next = { ...g.cardCategoryIds };
        if (input.categoryId) next[input.cardId] = input.categoryId;
        else delete next[input.cardId];
        return { ...g, cardCategoryIds: next };
      }),
    onError: rollbackTo<TabGraph>(qc, dagKeys.tabGraph(tabId)),
    onSettled: () => invalidateGraph(qc, tabId),
  });
}

export function useCreateCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { name: string; color: string | null }) =>
      api.post<Category>("/api/categories", input),
    onSuccess: () => qc.invalidateQueries({ queryKey: dagKeys.tabGraphs }),
  });
}

export function useUpdateCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { id: string; name?: string; color?: string | null }) => {
      const { id, ...patch } = input;
      return api.patch<Category>(`/api/categories/${id}`, patch);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: dagKeys.tabGraphs }),
  });
}

export function useDeleteCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`/api/categories/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: dagKeys.tabGraphs }),
  });
}
