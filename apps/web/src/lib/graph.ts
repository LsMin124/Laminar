/**
 * 탭 그래프(화면 집계) 읽기 훅 — 백엔드 com.laminar.graph(BFF)와 대칭 표면 (DX-2).
 * 캔버스·캘린더가 같은 캐시를 공유해 양방향 자동 반영된다.
 */
import { useQuery } from "@tanstack/react-query";
import { api } from "./api";
import { dagKeys } from "./dagKeys";
import type { TabGraph } from "./graphTypes";

export function useTabGraph(tabId: string | null) {
  return useQuery({
    queryKey: dagKeys.tabGraph(tabId ?? ""),
    queryFn: () => api.get<TabGraph>(`/api/tabs/${tabId}/graph`),
    enabled: !!tabId,
  });
}
