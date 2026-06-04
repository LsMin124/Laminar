import { useEffect, useMemo, useRef, useState } from "react";
import {
  useAddCardToGroup,
  useCreateCard,
  useCreateGroup,
  useCreateRelation,
  useDeleteCard,
  useDeleteGroup,
  useDeleteRelation,
  useMoveCard,
  useRemoveCardFromGroup,
  useTabGraph,
  useUpdateCard,
  type Card,
  type Group,
} from "../../lib/dag";
import { ApiError } from "../../lib/api";
import { useDialogs } from "../ui/DialogProvider";
import "./DagCanvas.css";

const PX_PER_DAY = 130;
const LEFT_PAD = 80;
const BACKLOG_W = 180;
const BAR_H = 60;
const MS_DAY = 86400000;
const BACKLOG_X = 8;
const MAX_SPAN_DAYS = 30;
const EDGE_ZONE = 48;
const PAN_SPEED = 14;

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

function shortDate(iso: string): string {
  const [, m, d] = iso.split("-");
  return `${Number(m)}/${Number(d)}`;
}

/** 카드 개략 날짜/시간 — "6/4", "6/4–6/9"(멀티데이), "6/4 14:00"(시간지정), 날짜 없으면 "미정". */
function cardMeta(c: Card): string {
  if (!c.startDate) return "미정";
  let r = shortDate(c.startDate);
  if (c.endDate && c.endDate !== c.startDate) r += `–${shortDate(c.endDate)}`;
  if (!c.allDay && c.startTime) r += ` ${c.startTime.slice(0, 5)}`;
  return r;
}

type DragMode = "move" | "resize-l" | "resize-r";
interface DragState {
  id: string;
  mode: DragMode;
  x: number;
  y: number;
  w: number;
  offsetX: number;
  offsetY: number;
  rightX: number;
  moved: boolean;
}

/**
 * DAG 캔버스 — 노드=카드 막대(가로 x=startDate~endDate 스팬, 세로 y=canvasY), 엣지=관계(A 끝→B 시작).
 * 막대 몸통 드래그=이동(span 보존), 좌/우 끝 핸들=리사이즈(start/end), "⇢"=관계 생성, 더블클릭=카드 생성.
 * 이전 일자로 이동해 선행 관계를 위반하면 그 화살표를 끊을지 확인 후 이동한다.
 */
export function DagCanvas({ tabId }: { tabId: string }) {
  const graph = useTabGraph(tabId);
  const createCard = useCreateCard(tabId);
  const updateCard = useUpdateCard(tabId);
  const moveCard = useMoveCard(tabId);
  const deleteCard = useDeleteCard(tabId);
  const createRelation = useCreateRelation(tabId);
  const deleteRelation = useDeleteRelation(tabId);
  const createGroup = useCreateGroup(tabId);
  const deleteGroup = useDeleteGroup(tabId);
  const addCardToGroup = useAddCardToGroup(tabId);
  const removeCardFromGroup = useRemoveCardFromGroup(tabId);
  const dialogs = useDialogs();

  const canvasRef = useRef<HTMLDivElement>(null);
  const [drag, setDrag] = useState<DragState | null>(null);
  const [linkSource, setLinkSource] = useState<string | null>(null);
  const [hideCompleted, setHideCompleted] = useState(false);
  const dragRef = useRef<DragState | null>(null);
  const lastPtRef = useRef<{ x: number; y: number } | null>(null);
  const panDirRef = useRef(0);
  const panRafRef = useRef<number | null>(null);

  const cards = useMemo(() => graph.data?.cards ?? [], [graph.data]);
  const relations = graph.data?.cardRelations ?? [];
  const groups = graph.data?.groups ?? [];
  const groupMembers = graph.data?.groupMembers ?? {};
  const isVisible = (c: Card) => !hideCompleted || !c.completed;

  // 시간축 origin은 ref로 한 번 고정한다. 매 렌더마다 카드 최소 날짜로 재계산하면, 한 카드를 더 이른
  // 날짜로 옮길 때 origin이 바뀌어 무관한 다른 카드들의 x좌표가 통째로 재배치된다("하나 옮겼는데 다른
  // 카드가 따라 움직임"). 카드가 origin보다 더 왼쪽으로 갈 때만(화면 밖 이탈 방지) 좌측으로 확장한다.
  const minCardMs = useMemo(() => {
    let min: number | null = null;
    for (const c of cards) {
      if (!c.startDate) continue;
      const ms = parseDate(c.startDate);
      if (min === null || ms < min) min = ms;
    }
    return min;
  }, [cards]);
  const originRef = useRef<number | null>(null);
  const didScrollRef = useRef(false);
  if (originRef.current === null) {
    originRef.current = (minCardMs ?? todayUtc()) - 30 * MS_DAY;
  } else if (minCardMs !== null && minCardMs < originRef.current) {
    originRef.current = minCardMs - 30 * MS_DAY;
  }
  const originMs = originRef.current;

  const dateToX = (ms: number) => ((ms - originMs) / MS_DAY) * PX_PER_DAY + LEFT_PAD;
  const xToDateMs = (x: number) =>
    originMs + Math.round((x - LEFT_PAD) / PX_PER_DAY) * MS_DAY;

  // 최초 로드 시 콘텐츠(가장 이른 카드/오늘)가 좌측에 보이도록 스크롤 — origin 좌측 여백을 건너뛴다.
  useEffect(() => {
    if (didScrollRef.current || !canvasRef.current || cards.length === 0) return;
    canvasRef.current.scrollLeft = Math.max(0, dateToX(minCardMs ?? todayUtc()) - 120);
    didScrollRef.current = true;
  }, [cards.length, minCardMs]);

  // 드래그 상태 미러(rAF 패닝 루프가 최신 drag를 읽도록) + 언마운트 시 패닝 정리.
  useEffect(() => {
    dragRef.current = drag;
  }, [drag]);
  useEffect(() => () => stopPan(), []);

  const baseY = new Map<string, number>();
  cards.forEach((c, i) => baseY.set(c.id, 60 + i * (BAR_H + 24)));

  function barWidth(c: Card): number {
    if (!c.startDate) return BACKLOG_W;
    if (c.endDate) {
      const span = (parseDate(c.endDate) - parseDate(c.startDate)) / MS_DAY + 1;
      return Math.max(PX_PER_DAY, span * PX_PER_DAY);
    }
    return PX_PER_DAY;
  }

  function nodeGeom(c: Card): { x: number; y: number; w: number } {
    if (drag && drag.id === c.id) return { x: drag.x, y: drag.y, w: drag.w };
    const x = c.startDate ? dateToX(parseDate(c.startDate)) : BACKLOG_X;
    const y = c.canvasY ?? baseY.get(c.id) ?? 60;
    return { x, y, w: barWidth(c) };
  }

  const maxX = cards.reduce((m, c) => {
    const g = nodeGeom(c);
    return Math.max(m, g.x + g.w + 240);
  }, 1600);
  const maxY = cards.reduce((m, c) => Math.max(m, nodeGeom(c).y + BAR_H + 240), 800);
  const days = Math.ceil((maxX - LEFT_PAD) / PX_PER_DAY) + 1;
  const todayX = dateToX(todayUtc());

  function canvasPoint(clientX: number, clientY: number) {
    const el = canvasRef.current;
    if (!el) return { x: 0, y: 0 };
    const r = el.getBoundingClientRect();
    return { x: clientX - r.left + el.scrollLeft, y: clientY - r.top + el.scrollTop };
  }

  /** cardId로 들어오는 관계 중 fromCard.start > newStart 인(시간강제 위반) 관계 id 목록. */
  function violatedIncoming(cardId: string, newStartMs: number): string[] {
    return relations
      .filter((r) => r.toCardId === cardId)
      .filter((r) => {
        const from = cards.find((c) => c.id === r.fromCardId);
        return from?.startDate ? parseDate(from.startDate) > newStartMs : false;
      })
      .map((r) => r.id);
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
          : m.includes("span")
            ? `기간은 최대 ${MAX_SPAN_DAYS}일까지입니다.`
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

  function onBodyDown(e: React.PointerEvent<HTMLDivElement>, c: Card) {
    e.stopPropagation();
    const p = canvasPoint(e.clientX, e.clientY);
    const g = nodeGeom(c);
    e.currentTarget.setPointerCapture(e.pointerId);
    setDrag({
      id: c.id,
      mode: "move",
      x: g.x,
      y: g.y,
      w: g.w,
      offsetX: p.x - g.x,
      offsetY: p.y - g.y,
      rightX: g.x + g.w,
      moved: false,
    });
  }

  function onHandleDown(e: React.PointerEvent<HTMLDivElement>, c: Card, mode: DragMode) {
    e.stopPropagation();
    const g = nodeGeom(c);
    e.currentTarget.setPointerCapture(e.pointerId);
    setDrag({
      id: c.id,
      mode,
      x: g.x,
      y: g.y,
      w: g.w,
      offsetX: 0,
      offsetY: 0,
      rightX: g.x + g.w,
      moved: false,
    });
  }

  function applyPointerToDrag(clientX: number, clientY: number) {
    const p = canvasPoint(clientX, clientY);
    setDrag((d) => {
      if (!d) return d;
      if (d.mode === "move") {
        const nx = Math.max(0, p.x - d.offsetX);
        const ny = Math.max(0, p.y - d.offsetY);
        return {
          ...d,
          x: nx,
          y: ny,
          moved: d.moved || Math.abs(nx - d.x) > 2 || Math.abs(ny - d.y) > 2,
        };
      }
      if (d.mode === "resize-r") {
        const nw = Math.max(PX_PER_DAY, p.x - d.x);
        return { ...d, w: nw, moved: true };
      }
      const nx = Math.max(0, Math.min(p.x, d.rightX - PX_PER_DAY));
      return { ...d, x: nx, w: d.rightX - nx, moved: true };
    });
  }

  function stopPan() {
    if (panRafRef.current != null) {
      cancelAnimationFrame(panRafRef.current);
      panRafRef.current = null;
    }
    panDirRef.current = 0;
  }

  // 가장자리 자동 스크롤 루프 — 마지막 포인터 위치 기준으로 캔버스를 밀고 드래그 카드를 따라 이동.
  function panStep() {
    const el = canvasRef.current;
    if (!el || panDirRef.current === 0) {
      panRafRef.current = null;
      return;
    }
    const maxScroll = el.scrollWidth - el.clientWidth;
    const next = Math.max(0, Math.min(maxScroll, el.scrollLeft + panDirRef.current * PAN_SPEED));
    if (next === el.scrollLeft) {
      panRafRef.current = null; // 경계 도달 — 정지
      return;
    }
    el.scrollLeft = next;
    // 드래그 중이면 카드가 포인터를 따라가도록 재적용(호버 중엔 스크롤만).
    if (dragRef.current && lastPtRef.current) {
      applyPointerToDrag(lastPtRef.current.x, lastPtRef.current.y);
    }
    panRafRef.current = requestAnimationFrame(panStep);
  }

  // 포인터 X가 좌우 경계 근처면 패닝 방향 설정 + 루프 시작/중지 (드래그·호버 공용).
  function updateEdgePan(clientX: number) {
    const el = canvasRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const localX = clientX - rect.left;
    if (localX < EDGE_ZONE) panDirRef.current = -1;
    else if (localX > rect.width - EDGE_ZONE) panDirRef.current = 1;
    else panDirRef.current = 0;
    if (panDirRef.current !== 0) {
      if (panRafRef.current == null) panRafRef.current = requestAnimationFrame(panStep);
    } else {
      stopPan();
    }
  }

  function onPointerMove(e: React.PointerEvent<HTMLDivElement>) {
    if (!drag) return;
    lastPtRef.current = { x: e.clientX, y: e.clientY };
    applyPointerToDrag(e.clientX, e.clientY);
    updateEdgePan(e.clientX);
  }

  async function handleClick(c: Card) {
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

  async function onPointerUp(c: Card) {
    if (!drag || drag.id !== c.id) return;
    stopPan();
    lastPtRef.current = null;
    const d = drag;
    if (!d.moved) {
      setDrag(null);
      if (d.mode === "move") await handleClick(c);
      return;
    }
    try {
      if (d.mode === "resize-r") {
        const startMs = c.startDate ? parseDate(c.startDate) : xToDateMs(d.x);
        let endMs = xToDateMs(d.x + d.w) - MS_DAY;
        if (endMs < startMs) endMs = startMs;
        if (endMs > startMs + MAX_SPAN_DAYS * MS_DAY) endMs = startMs + MAX_SPAN_DAYS * MS_DAY;
        moveCard.mutate({ cardId: c.id, endDate: fmtDate(endMs) }, { onError: reportError });
        return;
      }
      // move 또는 resize-l → startDate 변경
      let newStartMs = xToDateMs(d.x);
      if (d.mode === "resize-l" && c.endDate) {
        const endMs = parseDate(c.endDate);
        if (newStartMs > endMs) newStartMs = endMs;
      }
      const sever = violatedIncoming(c.id, newStartMs);
      if (sever.length > 0) {
        const ok = await dialogs.confirm({
          title: "선행 연결 끊기",
          message: `이 이동은 선행 화살표 ${sever.length}개를 끊습니다. 계속할까요?`,
          confirmLabel: "끊고 이동",
          danger: true,
        });
        if (!ok) return;
      }
      const input: {
        cardId: string;
        startDate: string;
        endDate?: string;
        canvasY?: number;
        severRelationIds: string[];
      } = { cardId: c.id, startDate: fmtDate(newStartMs), severRelationIds: sever };
      if (d.mode === "move") {
        input.canvasY = Math.round(d.y);
        if (c.startDate && c.endDate) {
          const delta = newStartMs - parseDate(c.startDate);
          input.endDate = fmtDate(parseDate(c.endDate) + delta);
        }
      }
      moveCard.mutate(input, { onError: reportError });
    } finally {
      setDrag(null);
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

  /** 그룹명 입력 — 이미 속한 그룹이면 제거, 있는 그룹이면 추가, 없으면 생성+추가(토글). */
  async function onAssignGroup(card: Card) {
    const name = await dialogs.prompt({
      title: "그룹",
      message: "그룹 이름 — 새 이름이면 생성, 이미 속한 그룹이면 제거",
      placeholder: "그룹 이름",
    });
    if (!name || !name.trim()) return;
    const trimmed = name.trim();
    const existing = groups.find((g) => g.name === trimmed);
    if (existing) {
      const inIt = (groupMembers[existing.id] ?? []).includes(card.id);
      if (inIt) removeCardFromGroup.mutate({ groupId: existing.id, cardId: card.id });
      else addCardToGroup.mutate({ groupId: existing.id, cardId: card.id });
    } else {
      const created = await createGroup.mutateAsync({ name: trimmed });
      await addCardToGroup.mutateAsync({ groupId: created.id, cardId: card.id });
    }
  }

  async function onDeleteGroup(g: Group) {
    const ok = await dialogs.confirm({
      title: "그룹 삭제",
      message: `"${g.name}" 그룹을 삭제할까요? (카드는 유지됩니다)`,
      confirmLabel: "삭제",
      danger: true,
    });
    if (ok) deleteGroup.mutate(g.id);
  }

  /** 시간 설정 — HH:MM 입력 시 allDay=false+startTime, 비우면 종일(allDay=true). */
  async function onSetTime(c: Card) {
    const current = c.allDay ? "" : (c.startTime?.slice(0, 5) ?? "");
    const v = await dialogs.prompt({
      title: "시간 설정",
      message: "HH:MM 형식 (비우면 종일)",
      placeholder: "14:00",
      defaultValue: current,
    });
    if (v === null) return;
    const trimmed = v.trim();
    if (!trimmed) {
      updateCard.mutate({ cardId: c.id, allDay: true });
      return;
    }
    const m = /^([01]?\d|2[0-3]):([0-5]\d)$/.exec(trimmed);
    if (!m) {
      await dialogs.alert({ title: "형식 오류", message: "HH:MM으로 입력하세요 (예: 09:30, 14:00)" });
      return;
    }
    updateCard.mutate({
      cardId: c.id,
      allDay: false,
      startTime: `${m[1].padStart(2, "0")}:${m[2]}`,
    });
  }

  return (
    <div className={`dag${linkSource ? " linking" : ""}`}>
      <div className="dag-toolbar">
        <span>
          빈 곳 <strong>더블클릭</strong>=카드 · 막대 드래그=이동 · 양 끝=기간 조절 ·{" "}
          <strong>⇢</strong>=연결 · 선 클릭=연결 삭제
        </span>
        <label className="dag-toggle">
          <input
            type="checkbox"
            checked={hideCompleted}
            onChange={(e) => setHideCompleted(e.target.checked)}
          />
          완료 숨기기
        </label>
        {linkSource && (
          <span>
            · <strong>연결 대상 카드를 클릭</strong>{" "}
            <button type="button" onClick={() => setLinkSource(null)}>
              취소
            </button>
          </span>
        )}
      </div>
      <div
        className="dag-canvas"
        ref={canvasRef}
        onDoubleClick={onCanvasDoubleClick}
        onPointerMove={(e) => {
          if (!dragRef.current) updateEdgePan(e.clientX);
        }}
        onPointerLeave={() => {
          if (!dragRef.current) stopPan();
        }}
      >
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

          {groups.map((grp) => {
            const members = (groupMembers[grp.id] ?? [])
              .map((id) => cards.find((c) => c.id === id))
              .filter((c): c is Card => !!c && isVisible(c));
            if (members.length === 0) return null;
            let minX = Infinity;
            let minY = Infinity;
            let maxX = -Infinity;
            let maxY = -Infinity;
            for (const m of members) {
              const gm = nodeGeom(m);
              minX = Math.min(minX, gm.x);
              minY = Math.min(minY, gm.y);
              maxX = Math.max(maxX, gm.x + gm.w);
              maxY = Math.max(maxY, gm.y + BAR_H);
            }
            const pad = 16;
            const color = grp.color ?? "#5a6a7a";
            return (
              <div
                key={grp.id}
                className="dag-group"
                style={{
                  left: minX - pad,
                  top: minY - pad - 8,
                  width: maxX - minX + pad * 2,
                  height: maxY - minY + pad * 2 + 8,
                  borderColor: color,
                }}
              >
                <span
                  className="dag-group-label"
                  style={{ color }}
                  onClick={() => onDeleteGroup(grp)}
                  title="클릭하여 그룹 삭제"
                >
                  {grp.name}
                </span>
              </div>
            );
          })}

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
              if (!from || !to || !isVisible(from) || !isVisible(to)) return null;
              const fg = nodeGeom(from);
              const tg = nodeGeom(to);
              return (
                <line
                  key={rel.id}
                  className="dag-edge"
                  x1={fg.x + fg.w}
                  y1={fg.y + BAR_H / 2}
                  x2={tg.x}
                  y2={tg.y + BAR_H / 2}
                  markerEnd="url(#dag-arrow)"
                  onClick={() => onDeleteRelation(rel.id)}
                />
              );
            })}
          </svg>

          {cards.filter(isVisible).map((c) => {
            const g = nodeGeom(c);
            const dated = !!c.startDate;
            return (
              <div
                key={c.id}
                className={`dag-node${c.completed ? " completed" : ""}${
                  linkSource === c.id ? " link-src" : ""
                }`}
                style={{ left: g.x, top: g.y, width: g.w, height: BAR_H }}
                onPointerDown={(e) => onBodyDown(e, c)}
                onPointerMove={onPointerMove}
                onPointerUp={() => onPointerUp(c)}
              >
                {dated && (
                  <div
                    className="dag-handle l"
                    onPointerDown={(e) => onHandleDown(e, c, "resize-l")}
                    onPointerMove={onPointerMove}
                    onPointerUp={() => onPointerUp(c)}
                  />
                )}
                <input
                  type="checkbox"
                  className="dag-node-check"
                  checked={c.completed}
                  onPointerDown={(e) => e.stopPropagation()}
                  onClick={(e) => e.stopPropagation()}
                  onChange={(e) =>
                    updateCard.mutate({ cardId: c.id, completed: e.target.checked })
                  }
                  title="완료 여부"
                />
                <div className="dag-node-body">
                  <div className="dag-node-title">{c.title || "(제목 없음)"}</div>
                  <div className="dag-node-meta">{cardMeta(c)}</div>
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
                      onAssignGroup(c);
                    }}
                    title="그룹에 추가/제거"
                  >
                    ▣
                  </button>
                  <button
                    type="button"
                    onPointerDown={(e) => e.stopPropagation()}
                    onClick={(e) => {
                      e.stopPropagation();
                      onSetTime(c);
                    }}
                    title="시간 설정"
                  >
                    ⏱
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
                {dated && (
                  <div
                    className="dag-handle r"
                    onPointerDown={(e) => onHandleDown(e, c, "resize-r")}
                    onPointerMove={onPointerMove}
                    onPointerUp={() => onPointerUp(c)}
                  />
                )}
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
