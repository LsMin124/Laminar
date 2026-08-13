/** 화이트보드(자유 배치 노드 + 관계 화살표) 데이터 훅 — cards.ts/optimistic.ts 패턴 복제. */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { QueryClient } from "@tanstack/react-query";
import { api } from "./api";
import { optimisticUpdate, rollbackTo } from "./optimistic";

type Uuid = string;

export type WhiteboardNodeKind = "MD" | "IMAGE" | "STICKY" | "SHAPE" | "TEXT" | "PEN" | "SECTION";

export interface WhiteboardNode {
  id: Uuid;
  tabId: Uuid;
  kind: WhiteboardNodeKind;
  x: number;
  y: number;
  width: number | null;
  height: number | null;
  /** 노드 제목/라벨. */
  text: string | null;
  bodyMd: string | null;
  /** 자유 속성(jsonb) — 이미지 노드는 attrs.attachmentId로 R2 첨부를 참조. */
  attrs: Record<string, unknown>;
}

export interface WhiteboardEdge {
  id: Uuid;
  tabId: Uuid;
  fromNodeId: Uuid;
  toNodeId: Uuid;
  relationKind: string;
  /** 라벨이 곧 화살표가 나타내는 관계. */
  label: string | null;
}

export interface WhiteboardGraph {
  tabId: Uuid;
  nodes: WhiteboardNode[];
  edges: WhiteboardEdge[];
}

const whiteboardKeys = {
  graph: (tabId: string) => ["whiteboard", tabId] as const,
};

function invalidate(qc: QueryClient, tabId: string): Promise<void> {
  return qc.invalidateQueries({ queryKey: whiteboardKeys.graph(tabId) });
}

export function useWhiteboardGraph(tabId: string | null) {
  return useQuery({
    queryKey: whiteboardKeys.graph(tabId ?? ""),
    queryFn: () => api.get<WhiteboardGraph>(`/api/tabs/${tabId}/whiteboard`),
    enabled: !!tabId,
  });
}

export function useCreateNode(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      kind: WhiteboardNodeKind;
      x: number;
      y: number;
      width?: number | null;
      height?: number | null;
      text?: string | null;
      bodyMd?: string | null;
      attrs?: Record<string, unknown>;
    }) => api.post<WhiteboardNode>("/api/whiteboard-nodes", { tabId, ...input }),
    onSettled: () => invalidate(qc, tabId),
  });
}

/** 이동·리사이즈·본문 편집 — 정의된 필드만 patch(undefined 생략 → 서버 무변경). 드롭 깜빡임 없이 낙관적 반영. */
export function useUpdateNode(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      nodeId: string;
      x?: number;
      y?: number;
      width?: number | null;
      height?: number | null;
      text?: string | null;
      bodyMd?: string | null;
      attrs?: Record<string, unknown>;
    }) => {
      const { nodeId, ...patch } = input;
      return api.patch<WhiteboardNode>(`/api/whiteboard-nodes/${nodeId}`, patch);
    },
    onMutate: (input) =>
      optimisticUpdate<WhiteboardGraph>(qc, whiteboardKeys.graph(tabId), (g) => ({
        ...g,
        nodes: g.nodes.map((n) =>
          n.id === input.nodeId
            ? {
                ...n,
                ...(input.x !== undefined ? { x: input.x } : {}),
                ...(input.y !== undefined ? { y: input.y } : {}),
                ...(input.width !== undefined ? { width: input.width } : {}),
                ...(input.height !== undefined ? { height: input.height } : {}),
                ...(input.text !== undefined ? { text: input.text } : {}),
                ...(input.bodyMd !== undefined ? { bodyMd: input.bodyMd } : {}),
                ...(input.attrs !== undefined ? { attrs: input.attrs } : {}),
              }
            : n,
        ),
      })),
    onError: rollbackTo<WhiteboardGraph>(qc, whiteboardKeys.graph(tabId)),
    onSettled: () => invalidate(qc, tabId),
  });
}

export function useDeleteNode(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (nodeId: string) => api.delete<void>(`/api/whiteboard-nodes/${nodeId}`),
    onMutate: (nodeId) =>
      optimisticUpdate<WhiteboardGraph>(qc, whiteboardKeys.graph(tabId), (g) => ({
        ...g,
        nodes: g.nodes.filter((n) => n.id !== nodeId),
        edges: g.edges.filter((e) => e.fromNodeId !== nodeId && e.toNodeId !== nodeId),
      })),
    onError: rollbackTo<WhiteboardGraph>(qc, whiteboardKeys.graph(tabId)),
    onSettled: () => invalidate(qc, tabId),
  });
}

/** WB-C undo — 노드 soft-delete 복구(같은 id 유지). 딸린 엣지는 삭제되지 않으므로 함께 재등장한다. */
export function useRestoreNode(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (nodeId: string) =>
      api.post<WhiteboardNode>(`/api/whiteboard-nodes/${nodeId}/restore`, {}),
    onSettled: () => invalidate(qc, tabId),
  });
}

export function useCreateEdge(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { fromNodeId: string; toNodeId: string; label?: string }) =>
      api.post<WhiteboardEdge>("/api/whiteboard-edges", input),
    onSettled: () => invalidate(qc, tabId),
  });
}

export function useDeleteEdge(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (edgeId: string) => api.delete<void>(`/api/whiteboard-edges/${edgeId}`),
    onSettled: () => invalidate(qc, tabId),
  });
}

/** WB-D — 엣지 끝점 재연결(from/to 중 지정한 쪽만 변경, 같은 id·라벨 유지). 낙관적 반영. */
export function useReconnectEdge(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { edgeId: string; fromNodeId?: string; toNodeId?: string }) =>
      api.post<WhiteboardEdge>(`/api/whiteboard-edges/${input.edgeId}/reconnect`, {
        fromNodeId: input.fromNodeId ?? null,
        toNodeId: input.toNodeId ?? null,
      }),
    onMutate: (input) =>
      optimisticUpdate<WhiteboardGraph>(qc, whiteboardKeys.graph(tabId), (g) => ({
        ...g,
        edges: g.edges.map((e) =>
          e.id === input.edgeId
            ? {
                ...e,
                ...(input.fromNodeId ? { fromNodeId: input.fromNodeId } : {}),
                ...(input.toNodeId ? { toNodeId: input.toNodeId } : {}),
              }
            : e,
        ),
      })),
    onError: rollbackTo<WhiteboardGraph>(qc, whiteboardKeys.graph(tabId)),
    onSettled: () => invalidate(qc, tabId),
  });
}

/** WB-C undo — 엣지 soft-delete 복구(같은 id 유지). */
export function useRestoreEdge(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (edgeId: string) =>
      api.post<WhiteboardEdge>(`/api/whiteboard-edges/${edgeId}/restore`, {}),
    onSettled: () => invalidate(qc, tabId),
  });
}

/** 엣지 라벨 수정 — 라벨이 곧 화살표의 관계. 깜빡임 없이 낙관적 반영. */
export function useUpdateEdge(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { edgeId: string; label: string | null }) =>
      api.patch<WhiteboardEdge>(`/api/whiteboard-edges/${input.edgeId}`, {
        label: input.label,
      }),
    onMutate: (input) =>
      optimisticUpdate<WhiteboardGraph>(qc, whiteboardKeys.graph(tabId), (g) => ({
        ...g,
        edges: g.edges.map((e) => (e.id === input.edgeId ? { ...e, label: input.label } : e)),
      })),
    onError: rollbackTo<WhiteboardGraph>(qc, whiteboardKeys.graph(tabId)),
    onSettled: () => invalidate(qc, tabId),
  });
}
