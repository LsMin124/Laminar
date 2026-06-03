import { useMemo, useRef, useState } from "react";
import {
  useCreateCard,
  useCreateRelation,
  useDeleteCard,
  useDeleteRelation,
  useTabGraph,
  useUpdateCard,
  type Card,
} from "../../lib/dag";
import { ApiError } from "../../lib/api";
import { useDialogs } from "../ui/DialogProvider";
import "./DagCanvas.css";

const PX_PER_DAY = 100;
const LEFT_PAD = 80;
const NODE_W = 180;
const ROW_H = 92;
const MS_DAY = 86400000;
const BACKLOG_X = 8;

function parseDate(s: string): number {
  const [y, m, d] = s.split("-").map(Number);
  return Date.UTC(y, m - 1, d);
}
function fmtDate(ms: number): string {
  const d = new Date(ms);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getUTCFullYear()}-${p(d.getUTCMonth() + 1)}-${p(d.getUTCDate())}`;
}
function todayUtc(): number {
  const n = new Date();
  return Date.UTC(n.getFullYear(), n.getMonth(), n.getDate());
}

interface DragState {
  id: string;
  x: number;
  y: number;
  offsetX: number;
  offsetY: number;
  moved: boolean;
}

/**
 * DAG 캔버스 — 노드=카드(가로 x=startDate, 세로 y=canvasY), 엣지=카드 관계.
 * 드래그=날짜·위치 변경(백엔드 시간강제·연쇄이동), "⇢"=관계 생성(사이클 차단), 더블클릭=카드 생성.
 * 모든 변경은 그래프 1회 재조회로 서버 진실에 정렬한다.
 */
export function DagCanvas({ tabId }: { tabId: string }) {
  const graph = useTabGraph(tabId);
  const createCard = useCreateCard(tabId);
  const updateCard = useUpdateCard(tabId);
  const deleteCard = useDeleteCard(tabId);
  const createRelation = useCreateRelation(tabId);
  const deleteRelation = useDeleteRelation(tabId);
  const dialogs = useDialogs();

  const canvasRef = useRef<HTMLDivElement>(null);
  const [drag, setDrag] = useState<DragState | null>(null);
  const [linkSource, setLinkSource] = useState<string | null>(null);

  const cards = useMemo(() => graph.data?.cards ?? [], [graph.data]);
  const relations = graph.data?.cardRelations ?? [];

  const originMs = useMemo(() => {
    let min = todayUtc();
    for (const c of cards) if (c.startDate) min = Math.min(min, parseDate(c.startDate));
    return min - 2 * MS_DAY;
  }, [cards]);

  const dateToX = (ms: number) =>
    ((ms - originMs) / MS_DAY) * PX_PER_DAY + LEFT_PAD;
  const xToDateMs = (x: number) =>
    originMs + Math.round((x - LEFT_PAD) / PX_PER_DAY) * MS_DAY;

  const baseY = new Map<string, number>();
  cards.forEach((c, i) => baseY.set(c.id, 60 + i * ROW_H));

  function nodePos(c: Card): { x: number; y: number } {
    if (drag && drag.id === c.id) return { x: drag.x, y: drag.y };
    const x = c.startDate ? dateToX(parseDate(c.startDate)) : BACKLOG_X;
    const y = c.canvasY ?? baseY.get(c.id) ?? 60;
    return { x, y };
  }

  const maxX = cards.reduce((m, c) => Math.max(m, nodePos(c).x + NODE_W + 240), 1600);
  const maxY = cards.reduce((m, c) => Math.max(m, nodePos(c).y + ROW_H + 240), 800);
  const days = Math.ceil((maxX - LEFT_PAD) / PX_PER_DAY) + 1;
  const todayX = dateToX(todayUtc());

  function canvasPoint(clientX: number, clientY: number) {
    const el = canvasRef.current;
    if (!el) return { x: 0, y: 0 };
    const r = el.getBoundingClientRect();
    return { x: clientX - r.left + el.scrollLeft, y: clientY - r.top + el.scrollTop };
  }

  async function reportError(err: unknown) {
    let msg = "작업에 실패했습니다.";
    if (err instanceof ApiError && err.status === 409) {
      const body = err.body as { message?: string } | string;
      const m = typeof body === "object" && body?.message ? body.message : "";
      msg = m.includes("cycle")
        ? "두 카드를 연결하면 순환이 생겨 차단되었습니다."
        : m.includes("predecessor")
          ? "선행 카드보다 앞 날짜로 옮길 수 없습니다."
          : "충돌이 발생했습니다.";
    }
    await dialogs.alert({ title: "처리 불가", message: msg });
  }

  async function onCanvasDoubleClick(e: React.MouseEvent<HTMLDivElement>) {
    if (e.target !== e.currentTarget) return;
    const p = canvasPoint(e.clientX, e.clientY);
    const title = await dialogs.prompt({ title: "새 카드", placeholder: "카드 제목" });
    if (!title || !title.trim()) return;
    const startDate = p.x >= LEFT_PAD ? fmtDate(xToDateMs(p.x)) : null;
    createCard.mutate({ title: title.trim(), startDate });
  }

  function onNodePointerDown(e: React.PointerEvent<HTMLDivElement>, c: Card) {
    e.stopPropagation();
    const p = canvasPoint(e.clientX, e.clientY);
    const pos = nodePos(c);
    e.currentTarget.setPointerCapture(e.pointerId);
    setDrag({
      id: c.id,
      x: pos.x,
      y: pos.y,
      offsetX: p.x - pos.x,
      offsetY: p.y - pos.y,
      moved: false,
    });
  }

  function onNodePointerMove(e: React.PointerEvent<HTMLDivElement>) {
    if (!drag) return;
    const p = canvasPoint(e.clientX, e.clientY);
    const nx = Math.max(0, p.x - drag.offsetX);
    const ny = Math.max(0, p.y - drag.offsetY);
    setDrag((d) =>
      d
        ? { ...d, x: nx, y: ny, moved: d.moved || Math.abs(nx - d.x) > 2 || Math.abs(ny - d.y) > 2 }
        : d,
    );
  }

  async function onNodePointerUp(c: Card) {
    if (!drag || drag.id !== c.id) return;
    const { x, y, moved } = drag;
    setDrag(null);
    if (moved) {
      const startDate = x >= LEFT_PAD - PX_PER_DAY / 2 ? fmtDate(xToDateMs(x)) : c.startDate;
      updateCard.mutate(
        { cardId: c.id, startDate, canvasY: Math.round(y) },
        { onError: reportError },
      );
      return;
    }
    if (linkSource && linkSource !== c.id) {
      const from = linkSource;
      setLinkSource(null);
      createRelation.mutate({ fromCardId: from, toCardId: c.id }, { onError: reportError });
    } else if (linkSource === c.id) {
      setLinkSource(null);
    } else {
      const title = await dialogs.prompt({ title: "카드 제목 편집", defaultValue: c.title });
      if (title && title.trim() && title !== c.title) {
        updateCard.mutate({ cardId: c.id, title: title.trim() });
      }
    }
  }

  async function onDeleteCard(c: Card) {
    const ok = await dialogs.confirm({
      title: "카드 삭제",
      message: `"${c.title}" 카드를 삭제할까요?`,
      confirmLabel: "삭제",
      danger: true,
    });
    if (ok) deleteCard.mutate(c.id);
  }

  async function onDeleteRelation(id: string) {
    const ok = await dialogs.confirm({ title: "연결 삭제", confirmLabel: "삭제", danger: true });
    if (ok) deleteRelation.mutate(id);
  }

  return (
    <div className={`dag${linkSource ? " linking" : ""}`}>
      <div className="dag-toolbar">
        <span>
          빈 곳 <strong>더블클릭</strong>=카드 · 드래그=이동(가로 날짜·세로 자유) ·{" "}
          <strong>⇢</strong>=연결 · 선 클릭=연결 삭제
        </span>
        {linkSource && (
          <span>
            · <strong>연결 대상 카드를 클릭</strong>{" "}
            <button type="button" onClick={() => setLinkSource(null)}>
              취소
            </button>
          </span>
        )}
      </div>
      <div className="dag-canvas" ref={canvasRef} onDoubleClick={onCanvasDoubleClick}>
        <div className="dag-surface" style={{ width: maxX, height: maxY }}>
          {Array.from({ length: days }, (_, i) => {
            const x = LEFT_PAD + i * PX_PER_DAY;
            const d = new Date(originMs + i * MS_DAY);
            return (
              <div key={`ax-${i}`}>
                <div className="dag-gridline" style={{ left: x }} />
                <div className="dag-axis-label" style={{ left: x }}>
                  {`${d.getUTCMonth() + 1}/${d.getUTCDate()}`}
                </div>
              </div>
            );
          })}
          <div className="dag-today" style={{ left: todayX }} />
          <div className="dag-backlog-label">날짜 미정</div>

          <svg className="dag-edges" width={maxX} height={maxY} aria-hidden="true">
            <defs>
              <marker
                id="dag-arrow"
                viewBox="0 0 10 10"
                refX="9"
                refY="5"
                markerWidth="7"
                markerHeight="7"
                orient="auto-start-reverse"
              >
                <path d="M0,0 L10,5 L0,10 z" fill="#4a90c2" />
              </marker>
            </defs>
            {relations.map((rel) => {
              const from = cards.find((c) => c.id === rel.fromCardId);
              const to = cards.find((c) => c.id === rel.toCardId);
              if (!from || !to) return null;
              const fp = nodePos(from);
              const tp = nodePos(to);
              return (
                <line
                  key={rel.id}
                  className="dag-edge"
                  x1={fp.x + NODE_W / 2}
                  y1={fp.y + 28}
                  x2={tp.x + NODE_W / 2}
                  y2={tp.y + 28}
                  markerEnd="url(#dag-arrow)"
                  onClick={() => onDeleteRelation(rel.id)}
                />
              );
            })}
          </svg>

          {cards.map((c) => {
            const p = nodePos(c);
            return (
              <div
                key={c.id}
                className={`dag-node${c.completed ? " completed" : ""}${
                  linkSource === c.id ? " link-src" : ""
                }`}
                style={{ left: p.x, top: p.y }}
                onPointerDown={(e) => onNodePointerDown(e, c)}
                onPointerMove={onNodePointerMove}
                onPointerUp={() => onNodePointerUp(c)}
              >
                <div className="dag-node-title">{c.title || "(제목 없음)"}</div>
                <div className="dag-node-meta">
                  <span>{c.startDate ?? "미정"}</span>
                  {c.completed && <span>✓</span>}
                </div>
                <div className="dag-node-actions">
                  <button
                    type="button"
                    onPointerDown={(e) => e.stopPropagation()}
                    onClick={(e) => {
                      e.stopPropagation();
                      setLinkSource(c.id);
                    }}
                    title="다른 카드와 연결"
                  >
                    ⇢
                  </button>
                  <button
                    type="button"
                    onPointerDown={(e) => e.stopPropagation()}
                    onClick={(e) => {
                      e.stopPropagation();
                      onDeleteCard(c);
                    }}
                    title="카드 삭제"
                  >
                    ✕
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </div>
      {graph.isLoading && <p className="loading">그래프 불러오는 중...</p>}
      {graph.isError && <p className="loading">그래프를 불러오지 못했습니다.</p>}
    </div>
  );
}
