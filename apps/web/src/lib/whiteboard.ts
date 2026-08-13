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

/** 캐시 그래프 직접 갱신 — refetch 없이 서버 응답을 반영한다(성공 시 재조회 폭풍 방지). */
function setGraph(
  qc: QueryClient,
  tabId: string,
  apply: (g: WhiteboardGraph) => WhiteboardGraph,
): void {
  qc.setQueryData<WhiteboardGraph>(whiteboardKeys.graph(tabId), (cur) =>
    cur === undefined ? cur : apply(cur),
  );
}

/** 실패 시 스냅샷 복원 + 서버 기준 재동기화 — 낙관적 훅 공통 onError. */
function rollbackAndResync(qc: QueryClient, tabId: string) {
  const rollback = rollbackTo<WhiteboardGraph>(qc, whiteboardKeys.graph(tabId));
  return (err: unknown, input: unknown, ctx: { prev?: WhiteboardGraph } | undefined) => {
    rollback(err, input, ctx);
    void invalidate(qc, tabId);
  };
}

let tempSeq = 0;
/** 낙관적 임시 id — 서버 응답 도착 즉시 실제 id로 치환된다. */
function nextTempId(): string {
  tempSeq += 1;
  return `temp-${tempSeq}`;
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
    // 렌더 우선 — 임시 노드를 즉시 그리고 서버 응답으로 치환한다(왕복 대기 없는 체감 즉시성).
    onMutate: async (input) => {
      const tempId = nextTempId();
      const snap = await optimisticUpdate<WhiteboardGraph>(qc, whiteboardKeys.graph(tabId), (g) => ({
        ...g,
        nodes: [
          ...g.nodes,
          {
            id: tempId,
            tabId,
            kind: input.kind,
            x: input.x,
            y: input.y,
            width: input.width ?? null,
            height: input.height ?? null,
            text: input.text ?? null,
            bodyMd: input.bodyMd ?? null,
            attrs: input.attrs ?? {},
          },
        ],
      }));
      return { ...snap, tempId };
    },
    onSuccess: (created, _input, ctx) => {
      setGraph(qc, tabId, (g) => ({
        ...g,
        nodes: g.nodes.map((n) => (n.id === ctx?.tempId ? created : n)),
      }));
    },
    onError: rollbackAndResync(qc, tabId),
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
    // 성공 시 재조회 없음 — 서버가 보낸 값을 그대로 echo하므로 낙관적 상태가 곧 서버 상태다.
    onError: rollbackAndResync(qc, tabId),
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
    onError: rollbackAndResync(qc, tabId),
  });
}

/** WB-C undo — 노드 soft-delete 복구(같은 id 유지). 딸린 엣지는 삭제되지 않으므로 함께 재등장한다. */
export function useRestoreNode(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (nodeId: string) =>
      api.post<WhiteboardNode>(`/api/whiteboard-nodes/${nodeId}/restore`, {}),
    // 복구된 노드는 즉시 그리고, 딸려 돌아오는 엣지는 재조회로 되찾는다.
    onSuccess: (restored) => {
      setGraph(qc, tabId, (g) =>
        g.nodes.some((n) => n.id === restored.id) ? g : { ...g, nodes: [...g.nodes, restored] },
      );
    },
    onSettled: () => invalidate(qc, tabId),
  });
}

export function useCreateEdge(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { fromNodeId: string; toNodeId: string; label?: string }) =>
      api.post<WhiteboardEdge>("/api/whiteboard-edges", input),
    onMutate: async (input) => {
      const tempId = nextTempId();
      const snap = await optimisticUpdate<WhiteboardGraph>(qc, whiteboardKeys.graph(tabId), (g) => ({
        ...g,
        edges: [
          ...g.edges,
          {
            id: tempId,
            tabId,
            fromNodeId: input.fromNodeId,
            toNodeId: input.toNodeId,
            relationKind: "default",
            label: input.label ?? null,
          },
        ],
      }));
      return { ...snap, tempId };
    },
    onSuccess: (created, _input, ctx) => {
      setGraph(qc, tabId, (g) => ({
        ...g,
        edges: g.edges.map((e) => (e.id === ctx?.tempId ? created : e)),
      }));
    },
    onError: rollbackAndResync(qc, tabId),
  });
}

export function useDeleteEdge(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (edgeId: string) => api.delete<void>(`/api/whiteboard-edges/${edgeId}`),
    onMutate: (edgeId) =>
      optimisticUpdate<WhiteboardGraph>(qc, whiteboardKeys.graph(tabId), (g) => ({
        ...g,
        edges: g.edges.filter((e) => e.id !== edgeId),
      })),
    onError: rollbackAndResync(qc, tabId),
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
    onError: rollbackAndResync(qc, tabId),
  });
}

/** WB-C undo — 엣지 soft-delete 복구(같은 id 유지). */
export function useRestoreEdge(tabId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (edgeId: string) =>
      api.post<WhiteboardEdge>(`/api/whiteboard-edges/${edgeId}/restore`, {}),
    onSuccess: (restored) => {
      setGraph(qc, tabId, (g) =>
        g.edges.some((e) => e.id === restored.id) ? g : { ...g, edges: [...g.edges, restored] },
      );
    },
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
    onError: rollbackAndResync(qc, tabId),
  });
}
