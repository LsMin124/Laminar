/** 탭(보드) 데이터 훅 — lib/dag.ts 리소스별 분리 (DX-2). */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, getCurrentSubjectId } from "./api";
import { dagKeys } from "./dagKeys";
import type { Tab } from "./graphTypes";
import { optimisticUpdate, rollbackTo } from "./optimistic";
import { slugify } from "./slug";

// tabs 키는 subjectId 스코프(Q6) — 헤더와 동일 소스로 읽어 키와 응답 주제를 일치시킨다.
function tabsKey() {
  return dagKeys.tabs(getCurrentSubjectId() ?? "");
}

export function useTabs() {
  return useQuery({
    queryKey: tabsKey(),
    queryFn: () => api.get<Tab[]>("/api/tabs"),
  });
}

export function useCreateTab() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ name, kind }: { name: string; kind?: "DAG" | "WHITEBOARD" }) =>
      api.post<Tab>("/api/tabs", { name, slug: slugify(name), kind }),
    onSuccess: () => qc.invalidateQueries({ queryKey: tabsKey() }),
  });
}

/** 탭(보드) 수정(이름·본문) — 본문 자동저장이 깜빡이지 않도록 tabs 캐시 낙관적 반영. */
export function useUpdateTab() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ tabId, name, bodyMd }: { tabId: string; name?: string; bodyMd?: string | null }) =>
      api.patch<Tab>(`/api/tabs/${tabId}`, { name, bodyMd }),
    onMutate: ({ tabId, name, bodyMd }) =>
      optimisticUpdate<Tab[]>(qc, tabsKey(), (list) =>
        list.map((t) =>
          t.id === tabId
            ? {
                ...t,
                ...(name !== undefined ? { name } : {}),
                ...(bodyMd !== undefined ? { bodyMd } : {}),
              }
            : t,
        ),
      ),
    onError: rollbackTo<Tab[]>(qc, tabsKey()),
    onSettled: () => qc.invalidateQueries({ queryKey: tabsKey() }),
  });
}

/** 탭 삭제(soft) — 목록에서 즉시 제거(낙관), 실패 시 복원. 활성 탭 보정은 URL 보정 effect가 담당. */
export function useDeleteTab() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (tabId: string) => api.delete<void>(`/api/tabs/${tabId}`),
    onMutate: (tabId) =>
      optimisticUpdate<Tab[]>(qc, tabsKey(), (list) => list.filter((t) => t.id !== tabId)),
    onError: rollbackTo<Tab[]>(qc, tabsKey()),
    onSettled: () => qc.invalidateQueries({ queryKey: tabsKey() }),
  });
}
