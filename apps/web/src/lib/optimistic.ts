import type { QueryClient } from "@tanstack/react-query";

/**
 * 낙관적 캐시 갱신 공통기 (DX-6) — cancel → snapshot → set 순서를 한 곳에 고정한다.
 * 과거 훅들은 set 후 cancel(후행)이라, 진행 중이던 refetch 응답이 낙관적 갱신 뒤에 도착하면
 * 화면을 과거 데이터로 되돌릴 수 있는 경쟁이 있었고 그 순서가 훅마다 복제돼 있었다.
 * onMutate에서 호출하고, onError엔 rollbackTo()를 넘긴다.
 */
export async function optimisticUpdate<T>(
  qc: QueryClient,
  key: readonly unknown[],
  apply: (current: T) => T,
): Promise<{ prev?: T }> {
  await qc.cancelQueries({ queryKey: key });
  const prev = qc.getQueryData<T>(key);
  qc.setQueryData<T>(key, (cur) => (cur === undefined ? cur : apply(cur)));
  return { prev };
}

/** optimisticUpdate의 onError 짝 — 스냅샷이 있으면 복원한다. */
export function rollbackTo<T>(qc: QueryClient, key: readonly unknown[]) {
  return (_err: unknown, _input: unknown, ctx: { prev?: T } | undefined) => {
    if (ctx?.prev !== undefined) qc.setQueryData(key, ctx.prev);
  };
}
