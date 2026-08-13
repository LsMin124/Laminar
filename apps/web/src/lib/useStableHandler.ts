import { useCallback, useEffect, useRef } from "react";

/**
 * 매 렌더 재생성되는 핸들러의 항등성을 고정 — memo된 자식이 함수 prop 때문에 매번
 * 재렌더되는 것을 막는다. 최신 구현은 ref로 참조하므로 stale closure 없음(pasteRef 관용구).
 * 화이트보드 렌더 최적화(v158)에서 검증된 패턴을 DAG 캔버스와 공유하기 위해 lib로 승격.
 */
export function useStableHandler<A extends unknown[]>(
  fn: (...args: A) => void,
): (...args: A) => void {
  const implRef = useRef(fn);
  useEffect(() => {
    implRef.current = fn;
  });
  // 항등성은 useCallback([])이 보장하고, ref는 호출 시점에만 읽는다(렌더 중 ref 접근 금지 규칙).
  return useCallback((...args: A) => implRef.current(...args), []);
}
