import { useEffect, useMemo, useRef, useState } from "react";
import {
  useCreateCard,
  useCreateGroupRelation,
  useCreateRelation,
  useDeleteCard,
  useDeleteGroup,
  useDeleteGroupRelation,
  useDeleteRelation,
  useMoveCard,
  useRemoveCardFromGroup,
  useSetCardCategory,
  useTabGraph,
  useUpdateCard,
  type Card,
  type Group,
} from "../../lib/dag";
import { ApiError } from "../../lib/api";
import { MS_DAY, parseDate, fmtDate, todayUtc } from "../../lib/dateUtil";
import {
  BACKLOG_X,
  BAR_H,
  EDGE_ZONE,
  FORWARD_BUFFER_DAYS,
  LEFT_PAD,
  MAX_SPAN_DAYS,
  PAN_SPEED,
  PX_PER_DAY,
  TODAY_VIEW_RATIO,
  barWidth,
  cardMeta,
  mdExcerpt,
} from "./dagGeometry";
import { useDialogs } from "../ui/DialogProvider";
import { CardCategoryTag } from "./CardCategoryTag";
import { DagEdges } from "./DagEdges";
import { DagGroups } from "./DagGroups";
import { DagToolbar } from "./DagToolbar";
import { NewCardDialog } from "./NewCardDialog";
import "./DagCanvas.css";

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
  onOpenGroup,
}: {
  tabId: string;
  onOpenCard?: (cardId: string, title: string) => void;
  onOpenGroup?: (groupId: string, title: string) => void;
}) {
  const graph = useTabGraph(tabId);
  const createCard = useCreateCard(tabId);
  const updateCard = useUpdateCard(tabId);
  const moveCard = useMoveCard(tabId);
  const deleteCard = useDeleteCard(tabId);
  const createRelation = useCreateRelation(tabId);
  const deleteRelation = useDeleteRelation(tabId);
  const deleteGroup = useDeleteGroup(tabId);
  const createGroupRelation = useCreateGroupRelation(tabId);
  const deleteGroupRelation = useDeleteGroupRelation(tabId);
  const removeCardFromGroup = useRemoveCardFromGroup(tabId);
  const setCardCategory = useSetCardCategory(tabId);
  const dialogs = useDialogs();

  const canvasRef = useRef<HTMLDivElement>(null);
  const [drag, setDrag] = useState<DragState | null>(null);
  const [linkSource, setLinkSource] = useState<string | null>(null);
  // 연결 핸들(nub) 드래그 — 임시 라인 좌표(sx,sy=출발 앵커, x,y=커서) + 출발 카드 id.
  const [linkLine, setLinkLine] = useState<{
    sx: number;
    sy: number;
    x: number;
    y: number;
  } | null>(null);
  const linkFromRef = useRef<string | null>(null);
  const [hideCompleted, setHideCompleted] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  // 그룹 연결 모드 — 라벨 ⇢ 버튼으로 출발 그룹 지정, 대상 그룹 ⇢ 클릭 시 화살표 생성.
  const [groupLinkSource, setGroupLinkSource] = useState<string | null>(null);
  // 새 카드 생성 다이얼로그 — null=닫힘, {date}=열림(일자 기본값).
  const [newCard, setNewCard] = useState<{ date: string | null } | null>(null);
  const dragRef = useRef<DragState | null>(null);
  const lastPtRef = useRef<{ x: number; y: number } | null>(null);
  const panDirRef = useRef(0);
  const panRafRef = useRef<number | null>(null);

  const cards = useMemo(() => graph.data?.cards ?? [], [graph.data]);
  const relations = graph.data?.cardRelations ?? [];
  const groups = graph.data?.groups ?? [];
  const groupRelations = graph.data?.groupRelations ?? [];
  const groupMembers = graph.data?.groupMembers ?? {};
  const categories = graph.data?.categories ?? [];
  const cardCategoryIds = graph.data?.cardCategoryIds ?? {};
  const isVisible = (c: Card) => !hideCompleted || !c.completed;
  // 본문 발췌는 카드 데이터 변경 시에만 계산(드래그 매 프레임 재계산 방지). 빈/이미지전용은 "…".
  const excerpts = useMemo(() => {
    const m = new Map<string, string>();
    for (const c of cards) {
      if (c.bodyMd && c.bodyMd.trim().length > 0) m.set(c.id, mdExcerpt(c.bodyMd) || "…");
    }
    return m;
  }, [cards]);

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

  // 좌우(오늘 위치)·상하(맨 위) 스크롤을 오늘 기준 화면으로 복귀.
  function scrollToToday() {
    const el = canvasRef.current;
    if (!el) return;
    el.scrollTo({
      left: Math.max(0, dateToX(todayUtc()) - el.clientWidth * TODAY_VIEW_RATIO),
      top: 0,
      behavior: "smooth",
    });
  }

  const baseY = new Map<string, number>();
  cards.forEach((c, i) => baseY.set(c.id, 60 + i * (BAR_H + 24)));

  // 카드별 관계 수(메타 인디케이터) + 오늘(지연 판정).
  const relCount = new Map<string, number>();
  for (const r of relations) {
    relCount.set(r.fromCardId, (relCount.get(r.fromCardId) ?? 0) + 1);
    relCount.set(r.toCardId, (relCount.get(r.toCardId) ?? 0) + 1);
  }
  const todayMs = todayUtc();

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

  // 날짜축 — 세로선(전체 높이, surface)과 라벨(sticky 헤더)을 분리. origin/일수에만 의존 → 메모이즈.
  const gridLines = useMemo(
    () =>
      Array.from({ length: days }, (_, i) => (
        <div key={`gl-${i}`} className="dag-gridline" style={{ left: LEFT_PAD + i * PX_PER_DAY }} />
      )),
    [days],
  );
  const axisLabels = useMemo(() => {
    const t = todayUtc();
    return Array.from({ length: days }, (_, i) => {
      const dayMs = originMs + i * MS_DAY;
      const d = new Date(dayMs);
      return (
        <div
          key={`ax-${i}`}
          className={`dag-axis-label${dayMs === t ? " today" : ""}`}
          style={{ left: LEFT_PAD + i * PX_PER_DAY }}
        >
          {`${d.getUTCMonth() + 1}/${d.getUTCDate()}`}
        </div>
      );
    });
  }, [days, originMs]);

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

  function onCanvasDoubleClick(e: React.MouseEvent<HTMLDivElement>) {
    if (e.target !== e.currentTarget) return;
    const p = canvasPoint(e.clientX, e.clientY);
    const startDate = p.x >= LEFT_PAD ? fmtDate(xToDateMs(p.x)) : null;
    setNewCard({ date: startDate });
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

  function onAddCard() {
    setNewCard({ date: fmtDate(todayUtc()) });
  }

  // 새 카드 확정 — 생성 후 분류 선택 시 지정(일자 비우면 미정/백로그).
  async function onCreateCard(input: {
    title: string;
    startDate: string | null;
    categoryId: string | null;
  }) {
    const card = await createCard.mutateAsync({
      title: input.title,
      startDate: input.startDate,
    });
    if (input.categoryId) {
      setCardCategory.mutate({ cardId: card.id, categoryId: input.categoryId });
    }
    setNewCard(null);
  }

  function onToolLink() {
    if (selectedIds.size !== 1) return;
    const [id] = [...selectedIds];
    setLinkSource(id);
    setSelectedIds(new Set());
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

  // 그룹 경계 박스 geometry(멤버 카드 bounding rect) — 박스 렌더와 그룹 화살표 앵커가 공유.
  // 멤버가 보이지 않으면 rect 없음(빈/숨김 그룹은 박스·화살표 모두 숨김). 카드 드래그를 따라 갱신.
  const groupRects = new Map<string, { x: number; y: number; w: number; h: number }>();
  for (const grp of groups) {
    const members = (groupMembers[grp.id] ?? [])
      .map((id) => cards.find((c) => c.id === id))
      .filter((c): c is Card => !!c && isVisible(c));
    if (members.length === 0) continue;
    let gx0 = Infinity;
    let gy0 = Infinity;
    let gx1 = -Infinity;
    let gy1 = -Infinity;
    for (const m of members) {
      const gm = nodeGeom(m);
      gx0 = Math.min(gx0, gm.x);
      gy0 = Math.min(gy0, gm.y);
      gx1 = Math.max(gx1, gm.x + gm.w);
      gy1 = Math.max(gy1, gm.y + BAR_H);
    }
    const pad = 16;
    groupRects.set(grp.id, {
      x: gx0 - pad,
      y: gy0 - pad - 8,
      w: gx1 - gx0 + pad * 2,
      h: gy1 - gy0 + pad * 2 + 8,
    });
  }

  async function onDeleteGroupRelation(id: string) {
    const ok = await dialogs.confirm({
      title: "그룹 연결 삭제",
      confirmLabel: "삭제",
      danger: true,
    });
    if (ok) deleteGroupRelation.mutate(id);
  }

  /**
   * 그룹 라벨 ⇢ 버튼 — 연결 모드 토글/완성. 처음 누르면 출발 그룹 지정(link-src 강조),
   * 다른 그룹의 ⇢를 누르면 화살표 생성(출발→대상, 중복·self 가드), 같은 그룹 ⇢ 재클릭은 취소.
   */
  function onGroupLinkBtn(grp: Group) {
    if (groupLinkSource === null) {
      setGroupLinkSource(grp.id);
      return;
    }
    if (groupLinkSource === grp.id) {
      setGroupLinkSource(null);
      return;
    }
    const exists = groupRelations.some(
      (gr) => gr.fromGroupId === groupLinkSource && gr.toGroupId === grp.id,
    );
    if (!exists) {
      createGroupRelation.mutate(
        { fromGroupId: groupLinkSource, toGroupId: grp.id },
        { onError: reportError },
      );
    }
    setGroupLinkSource(null);
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

  // 연결 핸들 nub: pointer capture로 드래그하다 다른 카드 위에서 놓으면 from→target 관계 생성.
  function onNubDown(e: React.PointerEvent<HTMLSpanElement>, c: Card) {
    e.stopPropagation();
    e.preventDefault();
    const g = nodeGeom(c);
    linkFromRef.current = c.id;
    setLinkLine({ sx: g.x + g.w, sy: g.y + BAR_H / 2, x: g.x + g.w, y: g.y + BAR_H / 2 });
    e.currentTarget.setPointerCapture(e.pointerId);
  }
  function onNubMove(e: React.PointerEvent<HTMLSpanElement>) {
    if (!linkFromRef.current) return;
    const p = canvasPoint(e.clientX, e.clientY);
    setLinkLine((l) => (l ? { ...l, x: p.x, y: p.y } : l));
  }
  function onNubUp(e: React.PointerEvent<HTMLSpanElement>) {
    const from = linkFromRef.current;
    linkFromRef.current = null;
    setLinkLine(null);
    if (!from) return;
    const p = canvasPoint(e.clientX, e.clientY);
    const target = cards.find((t) => {
      const tg = nodeGeom(t);
      return p.x >= tg.x && p.x <= tg.x + tg.w && p.y >= tg.y && p.y <= tg.y + BAR_H;
    });
    if (target && target.id !== from) {
      createRelation.mutate(
        { fromCardId: from, toCardId: target.id },
        { onError: reportError },
      );
    }
  }

  const selCount = selectedIds.size;
  const sole = selCount === 1 ? (cards.find((c) => selectedIds.has(c.id)) ?? null) : null;
  const soleInGroup =
    !!sole && groups.some((g) => (groupMembers[g.id] ?? []).includes(sole.id));
  const selectedCards = cards.filter((c) => selectedIds.has(c.id));

  return (
    <div className={`dag${linkSource || groupLinkSource ? " linking" : ""}`}>
      <DagToolbar
        tabId={tabId}
        categories={categories}
        cardCategoryIds={cardCategoryIds}
        groups={groups}
        groupMembers={groupMembers}
        selectedCards={selectedCards}
        sole={sole}
        soleInGroup={soleInGroup}
        selCount={selCount}
        hideCompleted={hideCompleted}
        setHideCompleted={setHideCompleted}
        linkSource={linkSource}
        groupLinkSource={groupLinkSource}
        onAddCard={onAddCard}
        onToolLink={onToolLink}
        onSetTime={onSetTime}
        onEditTitle={onEditTitle}
        onOpenCard={onOpenCard}
        onUngroup={onUngroup}
        onToolDelete={onToolDelete}
        scrollToToday={scrollToToday}
        setLinkSource={setLinkSource}
        setGroupLinkSource={setGroupLinkSource}
      />
      <div
        className="dag-canvas"
        ref={canvasRef}
        onDoubleClick={onCanvasDoubleClick}
        onPointerDown={() => {
          setSelectedIds(new Set());
          if (linkSource) setLinkSource(null);
          if (groupLinkSource) setGroupLinkSource(null);
        }}
        onPointerMove={(e) => {
          if (!dragRef.current) updateEdgePan(e.clientX);
        }}
        onPointerLeave={() => {
          if (!dragRef.current) stopPan();
        }}
      >
        <div className="dag-surface" style={{ width: maxX, height: maxY }}>
          {gridLines}
          <div className="dag-axis" style={{ width: maxX }}>
            {axisLabels}
          </div>
          <div className="dag-today" style={{ left: todayX, width: PX_PER_DAY }} />
          <div className="dag-backlog-label">날짜 미정</div>

          <DagGroups
            groups={groups}
            groupRects={groupRects}
            groupLinkSource={groupLinkSource}
            onOpenGroup={onOpenGroup}
            onGroupLinkBtn={onGroupLinkBtn}
            onDeleteGroup={onDeleteGroup}
          />

          <DagEdges
            maxX={maxX}
            maxY={maxY}
            groupRelations={groupRelations}
            groupRects={groupRects}
            relations={relations}
            cards={cards}
            nodeGeom={nodeGeom}
            isVisible={isVisible}
            linkLine={linkLine}
            onDeleteGroupRelation={onDeleteGroupRelation}
            onDeleteRelation={onDeleteRelation}
          />

          {cards.filter(isVisible).map((c) => {
            const g = nodeGeom(c);
            const dated = !!c.startDate;
            const excerpt = excerpts.get(c.id);
            const rels = relCount.get(c.id) ?? 0;
            const endMs = c.endDate
              ? parseDate(c.endDate)
              : c.startDate
                ? parseDate(c.startDate)
                : null;
            const overdue = endMs !== null && endMs < todayMs && !c.completed;
            const catId = cardCategoryIds[c.id] ?? null;
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
                <div className="dag-node-main">
                  <div className="dag-node-head">
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
                    <div className="dag-node-title">{c.title || "(제목 없음)"}</div>
                    <CardCategoryTag
                      tabId={tabId}
                      cardId={c.id}
                      categoryId={catId}
                      categories={categories}
                    />
                  </div>
                  <div className="dag-node-excerpt">
                    {excerpt ?? <span className="dag-node-empty">빈 문서</span>}
                  </div>
                  <div className="dag-node-foot">
                    <span className="dag-node-date">{cardMeta(c)}</span>
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
                    </div>
                  </div>
                </div>
                {dated && (
                  <div
                    className="dag-handle r"
                    onPointerDown={(e) => onHandleDown(e, c, "resize-r")}
                    onPointerMove={onPointerMove}
                    onPointerUp={() => onPointerUp(c)}
                  />
                )}
                <span
                  className="dag-link-nub"
                  title="드래그해 다음 카드로 연결"
                  onPointerDown={(e) => onNubDown(e, c)}
                  onPointerMove={onNubMove}
                  onPointerUp={onNubUp}
                />
              </div>
            );
          })}
        </div>
      </div>
      {graph.isLoading && <p className="loading">그래프 불러오는 중...</p>}
      {graph.isError && <p className="loading">그래프를 불러오지 못했습니다.</p>}
      {newCard && (
        <NewCardDialog
          defaultDate={newCard.date}
          categories={categories}
          onSubmit={onCreateCard}
          onClose={() => setNewCard(null)}
        />
      )}
    </div>
  );
}
