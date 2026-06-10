/** 탭(보드) 데이터 훅 — lib/dag.ts 리소스별 분리 (DX-2). */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api";
import { dagKeys } from "./dagKeys";
import type { Tab } from "./graphTypes";
import { optimisticUpdate, rollbackTo } from "./optimistic";
import { slugify } from "./slug";

export function useTabs() {
  return useQuery({
    queryKey: dagKeys.tabs,
    queryFn: () => api.get<Tab[]>("/api/tabs"),
  });
}

export function useCreateTab() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (name: string) =>
      api.post<Tab>("/api/tabs", { name, slug: slugify(name) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: dagKeys.tabs }),
  });
}

/** 탭(보드) 수정(이름·본문) — 본문 자동저장이 깜빡이지 않도록 tabs 캐시 낙관적 반영. */
export function useUpdateTab() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ tabId, name, bodyMd }: { tabId: string; name?: string; bodyMd?: string | null }) =>
      api.patch<Tab>(`/api/tabs/${tabId}`, { name, bodyMd }),
    onMutate: ({ tabId, name, bodyMd }) =>
      optimisticUpdate<Tab[]>(qc, dagKeys.tabs, (list) =>
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
    onError: rollbackTo<Tab[]>(qc, dagKeys.tabs),
    onSettled: () => qc.invalidateQueries({ queryKey: dagKeys.tabs }),
  });
}
