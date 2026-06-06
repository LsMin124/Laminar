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
// 우측(미래) 무한스크롤 여유 — 좌측 origin 30일 버퍼에 대응. 끝까지 끌면 자동 확장되므로 휴식 헤드룸만 확보.
const FORWARD_BUFFER_DAYS = 60;
// 진입 시 오늘을 뷰포트 가로 이 비율 지점에 배치(0.5=중앙, 0.4=살짝 좌측 → 미래 쪽을 더 넓게).
const TODAY_VIEW_RATIO = 0.4;
const EDGE_ZONE = 48;
const PAN_SPEED = 14;
const EDGE_STUB = 16;

/**
 * 카드 간 직각(꺾인) 엣지 경로 — 대각선 없이 가로·세로만.
 * - 같은 행: 곧은 수평선.
 * - 전방 여유 충분: A끝 → 중간 x에서 수직 → B시작 (대칭 엘보).
 * - 인접(다음날=간격 0)·겹침(B가 A보다 좌측): A 우측으로 스텁만큼 빠져나와 두 행 사이 레인으로
 *   되돌아온 뒤 B 좌측으로 우향 진입 → 항상 화살표가 오른쪽을 향하고 좌석이 끼이지 않는다.
 */
function edgePath(sx: number, sy: number, ex: number, ey: number): string {
  if (Math.abs(ey - sy) < 1) return `M ${sx} ${sy} H ${ex}`;
  if (ex - sx >= 2 * EDGE_STUB) {
    const mx = (sx + ex) / 2;
    return `M ${sx} ${sy} H ${mx} V ${ey} H ${ex}`;
  }
  const x1 = sx + EDGE_STUB;
  const x2 = ex - EDGE_STUB;
  const my = (sy + ey) / 2;
  return `M ${sx} ${sy} H ${x1} V ${my} H ${x2} V ${ey} H ${ex}`;
}

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
  shift: boolean;
}

/**
 * DAG 캔버스 — 노드=카드 막대(가로 x=startDate~endDate 스팬, 세로 y=canvasY), 엣지=관계(A 끝→B 시작).
 * 막대 몸통 드래그=이동(span 보존), 좌/우 끝 핸들=리사이즈(start/end), "⇢"=관계 생성, 더블클릭=카드 생성.
 * 이전 일자로 이동해 선행 관계를 위반하면 그 화살표를 끊을지 확인 후 이동한다.
 */
export function DagCanvas({
  tabId,
  onOpenCard,
}: {
  tabId: string;
  onOpenCard?: (cardId: string, title: string) => void;
}) {
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
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
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

  // 페이지 진입 시 오늘을 뷰포트 살짝 좌측(미래를 더 넓게)에 두도록 1회 스크롤(그래프 로드 후 origin 안정 시점).
  useEffect(() => {
    const el = canvasRef.current;
    if (didScrollRef.current || !el || !graph.data) return;
    el.scrollLeft = Math.max(0, dateToX(todayUtc()) - el.clientWidth * TODAY_VIEW_RATIO);
    didScrollRef.current = true;
  }, [graph.data]);

  // 드래그 상태 미러(rAF 패닝 루프가 최신 drag를 읽도록) + 언마운트 시 패닝 정리.
  useEffect(() => {
    dragRef.current = drag;
  }, [drag]);
  useEffect(() => () => stopPan(), []);

  const baseY = new Map<string, number>();
  cards.forEach((c, i) => baseY.set(c.id, 60 + i * (BAR_H + 24)));

  // 카드별 관계 수(메타 인디케이터) + 오늘(지연 판정).
  const relCount = new Map<string, number>();
  for (const r of relations) {
    relCount.set(r.fromCardId, (relCount.get(r.fromCardId) ?? 0) + 1);
    relCount.set(r.toCardId, (relCount.get(r.toCardId) ?? 0) + 1);
  }
  const todayMs = todayUtc();

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

  const todayX = dateToX(todayUtc());
  // 우측 경계 = (가장 오른쪽 카드 끝 또는 오늘) + 미래 버퍼. 카드를 더 우측으로 끌면 자동으로 더 늘어난다.
  const contentRightX = cards.reduce((m, c) => {
    const g = nodeGeom(c);
    return Math.max(m, g.x + g.w);
  }, todayX);
  const maxX = contentRightX + FORWARD_BUFFER_DAYS * PX_PER_DAY;
  const maxY = cards.reduce((m, c) => Math.max(m, nodeGeom(c).y + BAR_H + 240), 800);
  const days = Math.ceil((maxX - LEFT_PAD) / PX_PER_DAY) + 1;

  // 날짜축 그리드(선+라벨)는 origin/일수에만 의존 → 메모이즈해 드래그 매 프레임 재렌더에서 제외(부담 제거).
  const gridCells = useMemo(
    () =>
      Array.from({ length: days }, (_, i) => {
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
      }),
    [days, originMs],
  );

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
      shift: e.shiftKey,
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
      shift: false,
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

  async function onEditTitle(c: Card) {
    const title = await dialogs.prompt({ title: "카드 제목 편집", defaultValue: c.title });
    if (title && title.trim() && title !== c.title) {
      updateCard.mutate({ cardId: c.id, title: title.trim() });
    }
  }

  async function onPointerUp(c: Card) {
    if (!drag || drag.id !== c.id) return;
    stopPan();
    lastPtRef.current = null;
    const d = drag;
    if (!d.moved) {
      setDrag(null);
      if (d.mode === "move") {
        if (linkSource) {
          if (linkSource !== c.id) {
            createRelation.mutate(
              { fromCardId: linkSource, toCardId: c.id },
              { onError: reportError },
            );
          }
          setLinkSource(null);
        } else if (d.shift) {
          setSelectedIds((prev) => {
            const next = new Set(prev);
            if (next.has(c.id)) next.delete(c.id);
            else next.add(c.id);
            return next;
          });
        } else {
          setSelectedIds(new Set([c.id]));
        }
      }
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

  async function onToolDelete() {
    const sel = [...selectedIds];
    if (sel.length === 0) return;
    const ok = await dialogs.confirm({
      title: "카드 삭제",
      message: `선택한 ${sel.length}개 카드를 삭제할까요?`,
      confirmLabel: "삭제",
      danger: true,
    });
    if (!ok) return;
    for (const cardId of sel) deleteCard.mutate(cardId);
    setSelectedIds(new Set());
  }

  async function onDeleteRelation(id: string) {
    const ok = await dialogs.confirm({ title: "연결 삭제", confirmLabel: "삭제", danger: true });
    if (ok) deleteRelation.mutate(id);
  }

  async function onAddCard() {
    const title = await dialogs.prompt({ title: "새 카드", placeholder: "카드 제목" });
    if (!title || !title.trim()) return;
    createCard.mutate({ title: title.trim(), startDate: fmtDate(todayUtc()) });
  }

  function onToolLink() {
    if (selectedIds.size !== 1) return;
    const [id] = [...selectedIds];
    setLinkSource(id);
    setSelectedIds(new Set());
  }

  /** 선택한 카드들을 그룹으로 — 기존 이름이면 그 그룹에 추가, 새 이름이면 생성+추가. */
  async function onToolGroup() {
    const sel = [...selectedIds];
    if (sel.length === 0) return;
    const name = await dialogs.prompt({
      title: "그룹화",
      message: `선택한 ${sel.length}개 카드를 그룹으로 묶기 (기존 이름이면 그 그룹에 추가)`,
      placeholder: "그룹 이름",
    });
    if (!name || !name.trim()) return;
    const trimmed = name.trim();
    const existing = groups.find((g) => g.name === trimmed);
    const groupId = existing ? existing.id : (await createGroup.mutateAsync({ name: trimmed })).id;
    for (const cardId of sel) {
      await addCardToGroup.mutateAsync({ groupId, cardId });
    }
  }

  /** 선택한 카드를 속한 모든 그룹에서 제외. */
  function onUngroup() {
    for (const cardId of selectedIds) {
      for (const g of groups) {
        if ((groupMembers[g.id] ?? []).includes(cardId)) {
          removeCardFromGroup.mutate({ groupId: g.id, cardId });
        }
      }
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

  const selCount = selectedIds.size;
  const sole = selCount === 1 ? (cards.find((c) => selectedIds.has(c.id)) ?? null) : null;
  const soleInGroup =
    !!sole && groups.some((g) => (groupMembers[g.id] ?? []).includes(sole.id));

  return (
    <div className={`dag${linkSource ? " linking" : ""}`}>
      <div className="dag-toolbar">
        <button type="button" className="dag-tool" onClick={onAddCard} title="새 카드">
          ＋ 카드
        </button>
        <span className="dag-tool-sep" />
        <button
          type="button"
          className="dag-tool"
          disabled={!sole}
          onClick={onToolLink}
          title="선택 카드에서 연결 시작 (1개 선택)"
        >
          ⇢ 연결
        </button>
        <button
          type="button"
          className="dag-tool"
          disabled={!sole}
          onClick={() => {
            if (sole) onSetTime(sole);
          }}
          title="시간 설정 (1개 선택)"
        >
          ⏱ 시간
        </button>
        <button
          type="button"
          className="dag-tool"
          disabled={!sole}
          onClick={() => {
            if (sole) onEditTitle(sole);
          }}
          title="제목 편집 (1개 선택)"
        >
          ✎ 제목
        </button>
        <button
          type="button"
          className="dag-tool"
          disabled={!sole}
          onClick={() => {
            if (sole) onOpenCard?.(sole.id, sole.title);
          }}
          title="본문 열기 (1개 선택 · 카드 더블클릭도 가능)"
        >
          ▤ 본문
        </button>
        <span className="dag-tool-sep" />
        <button
          type="button"
          className="dag-tool"
          disabled={selCount < 1}
          onClick={onToolGroup}
          title="선택한 카드들을 그룹으로"
        >
          ▣ 그룹화
        </button>
        <button
          type="button"
          className="dag-tool"
          disabled={!soleInGroup}
          onClick={onUngroup}
          title="선택 카드를 그룹에서 제외"
        >
          ⊟ 그룹 해제
        </button>
        <button
          type="button"
          className="dag-tool danger"
          disabled={selCount < 1}
          onClick={onToolDelete}
          title="선택 카드 삭제"
        >
          ✕ 삭제{selCount > 0 ? ` (${selCount})` : ""}
        </button>
        <label className="dag-toggle">
          <input
            type="checkbox"
            checked={hideCompleted}
            onChange={(e) => setHideCompleted(e.target.checked)}
          />
          완료 숨기기
        </label>
        <span className="dag-hint">
          {linkSource ? (
            <>
              <strong>연결 대상 카드 클릭</strong>{" "}
              <button type="button" className="dag-tool" onClick={() => setLinkSource(null)}>
                취소
              </button>
            </>
          ) : selCount > 0 ? (
            `${selCount}개 선택됨 · Shift+클릭 다중 · 빈 곳 클릭 해제`
          ) : (
            "카드 클릭=선택 · 빈 곳 더블클릭=새 카드 · 드래그=이동/기간"
          )}
        </span>
      </div>
      <div
        className="dag-canvas"
        ref={canvasRef}
        onDoubleClick={onCanvasDoubleClick}
        onPointerDown={() => {
          setSelectedIds(new Set());
          if (linkSource) setLinkSource(null);
        }}
        onPointerMove={(e) => {
          if (!dragRef.current) updateEdgePan(e.clientX);
        }}
        onPointerLeave={() => {
          if (!dragRef.current) stopPan();
        }}
      >
        <div className="dag-surface" style={{ width: maxX, height: maxY }}>
          {gridCells}
          <div className="dag-today" style={{ left: todayX, width: PX_PER_DAY }}>
            <span className="dag-today-label">오늘</span>
          </div>
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
                <path d="M0,0 L10,5 L0,10 z" style={{ fill: "var(--accent-soft)" }} />
              </marker>
            </defs>
            {relations.map((rel) => {
              const from = cards.find((c) => c.id === rel.fromCardId);
              const to = cards.find((c) => c.id === rel.toCardId);
              if (!from || !to || !isVisible(from) || !isVisible(to)) return null;
              const fg = nodeGeom(from);
              const tg = nodeGeom(to);
              const d = edgePath(
                fg.x + fg.w,
                fg.y + BAR_H / 2,
                tg.x,
                tg.y + BAR_H / 2,
              );
              return (
                <path
                  key={rel.id}
                  className="dag-edge"
                  d={d}
                  markerEnd="url(#dag-arrow)"
                  onClick={() => onDeleteRelation(rel.id)}
                />
              );
            })}
          </svg>

          {cards.filter(isVisible).map((c) => {
            const g = nodeGeom(c);
            const dated = !!c.startDate;
            const hasBody = !!c.bodyMd && c.bodyMd.trim().length > 0;
            const rels = relCount.get(c.id) ?? 0;
            const endMs = c.endDate
              ? parseDate(c.endDate)
              : c.startDate
                ? parseDate(c.startDate)
                : null;
            const overdue = endMs !== null && endMs < todayMs && !c.completed;
            return (
              <div
                key={c.id}
                className={`dag-node${c.completed ? " completed" : ""}${
                  linkSource === c.id ? " link-src" : ""
                }${selectedIds.has(c.id) ? " selected" : ""}${overdue ? " overdue" : ""}`}
                style={{ left: g.x, top: g.y, width: g.w, height: BAR_H }}
                onPointerDown={(e) => onBodyDown(e, c)}
                onPointerMove={onPointerMove}
                onPointerUp={() => onPointerUp(c)}
                onDoubleClick={(e) => {
                  e.stopPropagation();
                  onOpenCard?.(c.id, c.title);
                }}
              >
                <span className="dag-node-stripe" />
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
                {(rels > 0 || hasBody || overdue) && (
                  <div className="dag-node-ind">
                    {overdue && (
                      <span className="dag-ind danger" title="지연(종료일 경과)">
                        ●
                      </span>
                    )}
                    {rels > 0 && (
                      <span className="dag-ind" title={`관계 ${rels}개`}>
                        ↔{rels}
                      </span>
                    )}
                    {hasBody && (
                      <span className="dag-ind" title="본문 있음">
                        ▤
                      </span>
                    )}
                  </div>
                )}
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
