/** 그룹 + 그룹 관계(화살표) + 그룹↔카드 멤버십 데이터 훅 — lib/dag.ts 리소스별 분리 (DX-2). */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api";
import { dagKeys, invalidateGraph } from "./dagKeys";
import type { Group, GroupRelation, TabGraph } from "./graphTypes";
import { optimisticUpdate, rollbackTo } from "./optimistic";

/** 그룹 단건 조회 — 본문(bodyMd)은 그래프 페이로드에서 빠져 단건으로만 온다(카드와 동형). */
export function useGroupById(groupId: string | null) {
  return useQuery({
    queryKey: dagKeys.group(groupId),
    queryFn: () => api.get<Group>(`/api/groups/${groupId}`),
    enabled: !!groupId,
    retry: false,
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

/** 그룹 속성(이름·색·본문) 수정 — 캔버스 표시 필드(이름·색)는 낙관적 반영. */
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
    onMutate: (input) =>
      optimisticUpdate<TabGraph>(qc, dagKeys.tabGraph(tabId), (g) => ({
        ...g,
        groups: g.groups.map((grp) =>
          grp.id === input.groupId
            ? {
                ...grp,
                ...(input.name !== undefined ? { name: input.name } : {}),
                ...(input.color !== undefined ? { color: input.color } : {}),
              }
            : grp,
        ),
      })),
    // 본문(bodyMd)은 그래프 페이로드에서 빠져 단건 캐시(useGroupById)에 산다 — PATCH 응답으로
    // 단건 캐시를 갱신해 본문 편집 후 '완료' 시 옛 본문으로 되돌아가지 않게 한다(카드와 동형).
    onSuccess: (data, input) => qc.setQueryData(dagKeys.group(input.groupId), data),
    onError: rollbackTo<TabGraph>(qc, dagKeys.tabGraph(tabId)),
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

/** 그룹 엣지 라벨(summary) 수정 — 라벨이 곧 화살표가 나타내는 관계. 낙관적 반영. */
export function useUpdateGroupRelation(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { relationId: string; summary: string | null }) =>
      api.patch<GroupRelation>(`/api/group-relations/${input.relationId}`, {
        summary: input.summary,
      }),
    onMutate: (input) =>
      optimisticUpdate<TabGraph>(qc, dagKeys.tabGraph(tabId), (g) => ({
        ...g,
        groupRelations: g.groupRelations.map((r) =>
          r.id === input.relationId ? { ...r, summary: input.summary } : r,
        ),
      })),
    onError: rollbackTo<TabGraph>(qc, dagKeys.tabGraph(tabId)),
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
