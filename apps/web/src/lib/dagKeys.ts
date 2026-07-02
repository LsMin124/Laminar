import type { QueryClient } from "@tanstack/react-query";

/**
 * DAG 작업면 쿼리 키 정본 (DX-2 키 팩토리) — 문자열 키가 훅마다 산재하지 않도록 한 곳에 고정.
 * 키 모양을 바꿀 일이 생기면 여기만 고친다.
 */
export const dagKeys = {
  subjects: ["subjects"] as const,
  /**
   * 주제별 탭 목록 — subjectId 스코프(Q6). /api/tabs는 헤더 기반이라 주제마다 내용이 다른데
   * 과거 비스코프 ["tabs"]를 공유해, 전환 직후 도착한 구 주제 응답이 새 주제 캐시에 착지할 수 있었다.
   * 헤더와 동일 소스(getCurrentSubjectId)로 키를 스코프해 주제별 슬롯을 분리한다.
   */
  tabs: (subjectId: string) => ["tabs", subjectId] as const,
  /** prefix 무효화용 — 카테고리처럼 주제 전역(전 탭) 영향 변경에 사용. tabGraph는 tabId(전역 유일)로 이미 스코프. */
  tabGraphs: ["tabGraph"] as const,
  tabGraph: (tabId: string) => ["tabGraph", tabId] as const,
  card: (cardId: string | null) => ["card", cardId] as const,
  group: (groupId: string | null) => ["group", groupId] as const,
};

/** 탭 그래프 무효화 — mutation onSettled 공용 한 줄. */
export function invalidateGraph(qc: QueryClient, tabId: string): Promise<void> {
  return qc.invalidateQueries({ queryKey: dagKeys.tabGraph(tabId) });
}
