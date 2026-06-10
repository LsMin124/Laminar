import { useEffect, useMemo, useRef, useState } from "react";
import { useMoveCard, useUpdateCard } from "../../lib/cards";
import { useTabGraph } from "../../lib/graph";
import type { Card } from "../../lib/graphTypes";
import { useRemoveCardFromGroup } from "../../lib/groups";
import { MAX_SPAN_DAYS } from "../../lib/cardRules";
import { MS_DAY, parseDate, fmtDate, todayUtc } from "../../lib/dateUtil";
import {
  BACKLOG_X,
  BAR_H,
  FORWARD_BUFFER_DAYS,
  LEFT_PAD,
  PX_PER_DAY,
  TODAY_VIEW_RATIO,
  barWidth,
  computeGroupRects,
} from "./dagGeometry";
import { useDialogs } from "../ui/DialogProvider";
import { DagAxis } from "./DagAxis";
import { DagEdges } from "./DagEdges";
import { DagGroups } from "./DagGroups";
import { DagNode } from "./DagNode";
import { DagToolbar } from "./DagToolbar";
import { NewCardDialog } from "./NewCardDialog";
import { useCardActions } from "./useCardActions";
import { useDagDrag, type DragMode } from "./useDagDrag";
import { useLinkMode } from "./useLinkMode";
import "./DagCanvas.css";

/**
 * 캔버스 x=0 기준 시각(origin) — 최소 카드 일자보다 30일 왼쪽. 카드가 과거로 추가되면 왼쪽으로만
 * 이동(단조 감소)하고, 카드 삭제로 최소값이 올라가도 오른쪽으로 되돌리지 않아 기존 노드들의
 * x 좌표 점프를 막는다. 이 "이전 렌더 기억"은 상태로 두면 한 프레임 늦은 좌표(재렌더 점프)가
 * 생기므로 렌더 중 ref 누산이 의도된 설계다. 반환값은 일반 숫자라 호출부는 규칙 위반이 없다.
 */
function useMonotonicOriginMs(minCardMs: number | null): number {
  const originRef = useRef<number | null>(null);
  /* eslint-disable react-hooks/refs -- 단조 origin 누산: 렌더 간 기억이 필요하나 상태화하면 1프레임 좌표 점프가 생긴다(위 주석). */
  if (originRef.current === null) {
    originRef.current = (minCardMs ?? todayUtc()) - 30 * MS_DAY;
  } else if (minCardMs !== null && minCardMs < originRef.current) {
    originRef.current = minCardMs - 30 * MS_DAY;
  }
  return originRef.current;
  /* eslint-enable react-hooks/refs */
}

/**
 * DAG 캔버스 — 노드=카드 막대(가로 x=startDate~endDate 스팬, 세로 y=canvasY), 엣지=관계(A 끝→B 시작).
 * 막대 몸통 드래그=이동(span 보존), 좌/우 끝 핸들=리사이즈(start/end), "⇢"=관계 생성, 더블클릭=카드 생성.
 * 이전 일자로 이동해 선행 관계를 위반하면 그 화살표를 끊을지 확인 후 이동한다.
 *
 * DX-11 분해 후 컨테이너 책무 = 좌표계(origin·date↔x)·드래그 커밋(moveCard)·선택. 연결 모드는
 * useLinkMode, 다이얼로그성 액션은 useCardActions, 날짜축은 DagAxis, 그룹 박스는 computeGroupRects.
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
  const updateCard = useUpdateCard(tabId);
  const moveCard = useMoveCard(tabId);
  const removeCardFromGroup = useRemoveCardFromGroup(tabId);
  const dialogs = useDialogs();

  // 드래그/리사이즈 + 가장자리 자동 스크롤 메커닉(상태 없는 좌표 변환·포인터 추적)은 훅으로 분리.
  // drag/setDrag·canvasRef·stopPan 등은 노출받아 컨테이너가 드롭 커밋·선택 타이밍을 직접 제어한다.
  const {
    canvasRef,
    drag,
    setDrag,
    dragRef,
    lastPtRef,
    canvasPoint,
    beginMove,
    beginResize,
    handleDragMove,
    updateEdgePan,
    stopPan,
  } = useDagDrag();
  const [hideCompleted, setHideCompleted] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  // 새 카드 생성 다이얼로그 — null=닫힘, {date}=열림(일자 기본값).
  const [newCard, setNewCard] = useState<{ date: string | null } | null>(null);

  const cards = useMemo(() => graph.data?.cards ?? [], [graph.data]);
  const relations = graph.data?.cardRelations ?? [];
  const groups = graph.data?.groups ?? [];
  const groupRelations = graph.data?.groupRelations ?? [];
  const groupMembers = graph.data?.groupMembers ?? {};
  const categories = graph.data?.categories ?? [];
  const cardCategoryIds = graph.data?.cardCategoryIds ?? {};
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
  const didScrollRef = useRef(false);
  const originMs = useMonotonicOriginMs(minCardMs);

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

  // 다이얼로그성 액션(제목·시간·삭제·라벨·생성)과 연결 모드(카드 nub·그룹 ⇢)는 훅으로 분리 (DX-11).
  const actions = useCardActions({
    tabId,
    selectedIds,
    setSelectedIds,
    closeNewCard: () => setNewCard(null),
  });
  const link = useLinkMode({
    tabId,
    cards,
    groupRelations,
    nodeGeom,
    canvasPoint,
    onError: actions.reportError,
  });

  function onCanvasDoubleClick(e: React.MouseEvent<HTMLDivElement>) {
    if (e.target !== e.currentTarget) return;
    const p = canvasPoint(e.clientX, e.clientY);
    const startDate = p.x >= LEFT_PAD ? fmtDate(xToDateMs(p.x)) : null;
    setNewCard({ date: startDate });
  }

  function onBodyDown(e: React.PointerEvent<HTMLDivElement>, c: Card) {
    beginMove(e, c.id, nodeGeom(c));
  }

  function onHandleDown(e: React.PointerEvent<HTMLDivElement>, c: Card, mode: DragMode) {
    beginResize(e, c.id, nodeGeom(c), mode);
  }

  async function onPointerUp(c: Card) {
    if (!drag || drag.id !== c.id) return;
    stopPan();
    lastPtRef.current = null;
    const d = drag;
    if (!d.moved) {
      setDrag(null);
      if (d.mode === "move") {
        if (link.linkSource) {
          link.completeCardLink(c.id);
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
        moveCard.mutate(
          { cardId: c.id, endDate: fmtDate(endMs) },
          { onError: actions.reportError },
        );
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
      moveCard.mutate(input, { onError: actions.reportError });
    } finally {
      setDrag(null);
    }
  }

  function onAddCard() {
    setNewCard({ date: fmtDate(todayUtc()) });
  }

  function onToolLink() {
    if (selectedIds.size !== 1) return;
    const [id] = [...selectedIds];
    link.setLinkSource(id);
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

  // 그룹 경계 박스 — 박스 렌더와 그룹 화살표 앵커가 공유(드래그 좌표 추종, dagGeometry 순수함수).
  const groupRects = computeGroupRects(groups, groupMembers, cards, isVisible, nodeGeom);

  const selCount = selectedIds.size;
  const sole = selCount === 1 ? (cards.find((c) => selectedIds.has(c.id)) ?? null) : null;
  const soleInGroup =
    !!sole && groups.some((g) => (groupMembers[g.id] ?? []).includes(sole.id));
  const selectedCards = cards.filter((c) => selectedIds.has(c.id));

  return (
    <div className={`dag${link.linkSource || link.groupLinkSource ? " linking" : ""}`}>
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
        linkSource={link.linkSource}
        groupLinkSource={link.groupLinkSource}
        onAddCard={onAddCard}
        onToolLink={onToolLink}
        onSetTime={actions.onSetTime}
        onEditTitle={actions.onEditTitle}
        onOpenCard={onOpenCard}
        onUngroup={onUngroup}
        onToolDelete={actions.onToolDelete}
        scrollToToday={scrollToToday}
        setLinkSource={link.setLinkSource}
        setGroupLinkSource={link.setGroupLinkSource}
      />
      <div
        className="dag-canvas"
        ref={canvasRef}
        onDoubleClick={onCanvasDoubleClick}
        onPointerDown={() => {
          setSelectedIds(new Set());
          if (link.linkSource) link.setLinkSource(null);
          if (link.groupLinkSource) link.setGroupLinkSource(null);
        }}
        onPointerMove={(e) => {
          if (!dragRef.current) updateEdgePan(e.clientX);
        }}
        onPointerLeave={() => {
          if (!dragRef.current) stopPan();
        }}
      >
        <div className="dag-surface" style={{ width: maxX, height: maxY }}>
          <DagAxis days={days} originMs={originMs} maxX={maxX} />
          <div className="dag-today" style={{ left: todayX, width: PX_PER_DAY }} />
          <div className="dag-backlog-label">날짜 미정</div>

          <DagGroups
            groups={groups}
            groupRects={groupRects}
            groupLinkSource={link.groupLinkSource}
            onOpenGroup={onOpenGroup}
            onGroupLinkBtn={link.onGroupLinkBtn}
            onDeleteGroup={actions.onDeleteGroup}
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
            linkLine={link.linkLine}
            onDeleteGroupRelation={actions.onDeleteGroupRelation}
            onDeleteRelation={actions.onDeleteRelation}
            onEditLabel={actions.onEditEdgeLabel}
            onEditGroupLabel={actions.onEditGroupEdgeLabel}
          />

          {cards.filter(isVisible).map((c) => {
            const endMs = c.endDate
              ? parseDate(c.endDate)
              : c.startDate
                ? parseDate(c.startDate)
                : null;
            const overdue = endMs !== null && endMs < todayMs && !c.completed;
            return (
              <DagNode
                key={c.id}
                card={c}
                geom={nodeGeom(c)}
                selected={selectedIds.has(c.id)}
                isLinkSource={link.linkSource === c.id}
                overdue={overdue}
                excerpt={c.bodyExcerpt ?? undefined}
                rels={relCount.get(c.id) ?? 0}
                categoryId={cardCategoryIds[c.id] ?? null}
                tabId={tabId}
                categories={categories}
                onBodyDown={onBodyDown}
                onHandleDown={onHandleDown}
                onPointerMove={handleDragMove}
                onPointerUp={onPointerUp}
                onOpenCard={onOpenCard}
                onToggleComplete={(cardId, completed) => updateCard.mutate({ cardId, completed })}
                onNubDown={link.onNubDown}
                onNubMove={link.onNubMove}
                onNubUp={link.onNubUp}
              />
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
          onSubmit={actions.onCreateCard}
          onClose={() => setNewCard(null)}
        />
      )}
    </div>
  );
}
