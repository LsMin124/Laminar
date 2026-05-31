import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router";
import {
  addDays,
  addMonths,
  differenceInCalendarDays,
  endOfMonth,
  format,
  parseISO,
  startOfMonth,
} from "date-fns";
import type { CardResponse, TabResponse } from "../lib/types";
import { MonthGrid } from "../components/calendar/MonthGrid";
import { BoardGraph } from "../components/graph/BoardGraph";
import { SwimlaneTimeline } from "../components/timeline/SwimlaneTimeline";
import { GroupManager } from "../components/group/GroupManager";
import {
  CardForm,
  emptyCardForm,
  type CardFormValues,
} from "../components/card/CardForm";
import { CardDialog } from "../components/card/CardDialog";
import { CardInspector } from "../components/card/CardInspector";
import { TabTreeSidebar } from "../components/tab/TabTreeSidebar";
import { PerpetualPanel } from "../components/perpetual/PerpetualPanel";
import { PerpetualNoteInspector } from "../components/perpetual/PerpetualNoteInspector";
import { useDialogs } from "../components/ui/DialogProvider";
import {
  useAddCardToGroup,
  useAddGroupToTab,
  useBoard,
  useBoardCalendar,
  useBoardGraph,
  useBoardTabs,
  useCreateCard,
  useCreateCardRelation,
  useCreateGroup,
  useMoveCard,
  useRemoveGroupFromTab,
  useRescheduleCard,
} from "../lib/queries";
import "./BoardDetailPage.css";

type ViewMode = "timeline" | "calendar" | "graph";
const TIMELINE_DAYS = 14;

export function BoardDetailPage() {
  const params = useParams();
  const navigate = useNavigate();
  const boardId = params.boardId ?? "";
  const [anchor, setAnchor] = useState<Date>(() => startOfMonth(new Date()));
  const [timelineStart, setTimelineStart] = useState<Date>(() =>
    addDays(new Date(), -2),
  );
  const [viewMode, setViewMode] = useState<ViewMode>("timeline");
  const [createInitialDate, setCreateInitialDate] = useState<string | null>(
    null,
  );
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null);
  const [selectedNoteId, setSelectedNoteId] = useState<string | null>(null);
  const [selectedTabId, setSelectedTabId] = useState<string | null>(null);

  const board = useBoard(boardId);
  // P4b 탭 스코프가 양 렌즈(캘린더·캔버스)에 필요하므로 그래프(멤버십 포함)를 상시 로드.
  const graph = useBoardGraph(boardId);
  const range = useMemo(() => {
    const from = startOfMonth(anchor);
    const to = endOfMonth(anchor);
    return {
      from: format(from, "yyyy-MM-dd"),
      to: format(to, "yyyy-MM-dd"),
    };
  }, [anchor]);
  const calendar = useBoardCalendar(boardId, range.from, range.to);
  const dialogs = useDialogs();
  const createCard = useCreateCard(boardId);
  const reschedule = useRescheduleCard(boardId);
  const createRelation = useCreateCardRelation(boardId);
  const moveCard = useMoveCard(boardId);
  const createGroup = useCreateGroup(boardId);
  const addToGroup = useAddCardToGroup(boardId);
  const tabs = useBoardTabs(boardId);
  const addGroupToTab = useAddGroupToTab(boardId);
  const removeGroupFromTab = useRemoveGroupFromTab(boardId);

  // P4b — 선택 탭 → (§3.4 visible 흡수 줌) 흡수 탭들의 멤버 그룹(tabGroups) →
  // 그 그룹들의 카드(groupMembers) = 스코프 카드 집합.
  // visible 부모는 후손 탭을 흡수(서브트리 포함), hidden 부모는 자기 그룹만(후손 미흡수).
  const scopedCardIds = useMemo(() => {
    if (!selectedTabId || !graph.data) return null;
    const childrenByParent = new Map<string | null, string[]>();
    const tabsById = new Map<string, TabResponse>();
    (tabs.data ?? []).forEach((t) => {
      tabsById.set(t.id, t);
      const arr = childrenByParent.get(t.parentTabId) ?? [];
      arr.push(t.id);
      childrenByParent.set(t.parentTabId, arr);
    });
    const inScopeTabs = new Set<string>([selectedTabId]);
    const stack = [selectedTabId];
    while (stack.length > 0) {
      const tid = stack.pop()!;
      if (tabsById.get(tid)?.visible) {
        (childrenByParent.get(tid) ?? []).forEach((c) => {
          if (!inScopeTabs.has(c)) {
            inScopeTabs.add(c);
            stack.push(c);
          }
        });
      }
    }
    const ids = new Set<string>();
    inScopeTabs.forEach((tid) => {
      (graph.data!.tabGroups[tid] ?? []).forEach((gid) => {
        (graph.data!.groupMembers[gid] ?? []).forEach((cid) => ids.add(cid));
      });
    });
    return ids;
  }, [selectedTabId, graph.data, tabs.data]);
  const selectedTabName =
    tabs.data?.find((t) => t.id === selectedTabId)?.name ?? "";
  const tabGroupIds =
    selectedTabId && graph.data ? (graph.data.tabGroups[selectedTabId] ?? []) : [];
  const boardGroups = graph.data?.groups ?? [];

  // P3b — 화살표 그리면 관계 생성 + 그룹 자동형성(§3.7). 둘 다 미그룹 → 새 그룹,
  // 한쪽만 그룹 → 다른쪽 합류. 양쪽 다른 그룹/다중 그룹은 흡수 모달(후속)이라 지금은 관계만.
  async function handleCreateRelation(fromCardId: string, toCardId: string) {
    await createRelation.mutateAsync({ fromCardId, toCardId });
    const gm = graph.data?.groupMembers ?? {};
    const aGroups = Object.keys(gm).filter((g) => gm[g]?.includes(fromCardId));
    const bGroups = Object.keys(gm).filter((g) => gm[g]?.includes(toCardId));
    if (aGroups.some((g) => bGroups.includes(g))) return;
    if (aGroups.length === 0 && bGroups.length === 0) {
      const group = await createGroup.mutateAsync({ name: "새 그룹" });
      await addToGroup.mutateAsync({ groupId: group.id, cardId: fromCardId });
      await addToGroup.mutateAsync({ groupId: group.id, cardId: toCardId });
    } else if (aGroups.length === 0 && bGroups.length === 1) {
      await addToGroup.mutateAsync({ groupId: bGroups[0], cardId: fromCardId });
    } else if (bGroups.length === 0 && aGroups.length === 1) {
      await addToGroup.mutateAsync({ groupId: aGroups[0], cardId: toCardId });
    }
  }

  function handleMoveCard(cardId: string, x: number, y: number) {
    const card = graph.data?.cards.find((c) => c.id === cardId);
    const attrs = {
      ...(card?.attrs ?? {}),
      canvasX: Math.round(x),
      canvasY: Math.round(y),
    };
    moveCard.mutate({ cardId, attrs });
  }

  async function handleReschedule(card: CardResponse, newStartIso: string) {
    // 드롭한 날짜를 새 시작일로, 기존 기간(일수)을 보존해 종료일 이동.
    const durationDays =
      card.startDate && card.endDate
        ? differenceInCalendarDays(parseISO(card.endDate), parseISO(card.startDate))
        : 0;
    const newEnd = card.endDate
      ? format(addDays(parseISO(newStartIso), durationDays), "yyyy-MM-dd")
      : null;
    await reschedule.mutateAsync({
      cardId: card.id,
      startDate: newStartIso,
      endDate: newEnd,
    });
  }

  async function handleCreate(values: CardFormValues) {
    await createCard.mutateAsync({
      boardId,
      title: values.title,
      bodyMd: values.bodyMd || undefined,
      startDate: values.startDate || null,
      endDate: values.endDate || null,
      startTime: values.startTime || null,
      allDay: values.allDay,
      importance: values.importance,
      rrule: values.rrule || null,
    });
    setCreateInitialDate(null);
  }

  async function handleCreateCardInCell(groupId: string, dateIso: string) {
    const title = await dialogs.prompt({
      title: "새 카드",
      placeholder: "카드 제목",
    });
    if (!title?.trim()) return;
    const card = await createCard.mutateAsync({
      boardId,
      title: title.trim(),
      startDate: dateIso,
    });
    addToGroup.mutate({ groupId, cardId: card.id });
  }

  async function handleAddNextStep(groupId: string, fromCard: CardResponse) {
    const title = await dialogs.prompt({
      title: "다음 단계 카드",
      placeholder: "카드 제목",
      message: `"${fromCard.title}" 다음 단계`,
    });
    if (!title?.trim()) return;
    const nextDate = fromCard.startDate
      ? format(addDays(parseISO(fromCard.startDate), 1), "yyyy-MM-dd")
      : null;
    const card = await createCard.mutateAsync({
      boardId,
      title: title.trim(),
      startDate: nextDate,
      importance: fromCard.importance,
    });
    await addToGroup.mutateAsync({ groupId, cardId: card.id });
    await createRelation.mutateAsync({
      fromCardId: fromCard.id,
      toCardId: card.id,
      relationKind: "SEQUENCE",
      summary: "다음 단계",
    });
  }

  return (
    <div className="board-workspace">
      <div className="board-sidebar">
        <PerpetualPanel
          boardId={boardId}
          selectedTabId={selectedTabId}
          selectedNoteId={selectedNoteId}
          onSelectNote={(id) => {
            setSelectedNoteId(id);
            setSelectedCardId(null);
          }}
        />
        <TabTreeSidebar
          boardId={boardId}
          selectedTabId={selectedTabId}
          onSelectTab={setSelectedTabId}
        />
      </div>
      <div className="board-detail">
      <header className="board-detail-header">
        <button
          type="button"
          className="board-detail-back"
          onClick={() => navigate("/")}
        >
          ← 보드 목록
        </button>
        <div className="board-detail-title-wrap">
          <h1 className="board-detail-title">
            {board.data?.name ?? "불러오는 중..."}
          </h1>
          {board.data && (
            <span className="board-detail-slug">/{board.data.slug}</span>
          )}
        </div>
        <div className="board-detail-header-actions">
          <button
            type="button"
            className="board-detail-perpetual"
            onClick={() => navigate(`/boards/${boardId}/perpetual`)}
          >
            영구노트
          </button>
          <button
            type="button"
            className="board-detail-create"
            onClick={() => setCreateInitialDate("")}
          >
            + 카드 추가
          </button>
        </div>
      </header>
      <div className="board-detail-tabs">
        <button
          type="button"
          className={`board-detail-tab${viewMode === "timeline" ? " active" : ""}`}
          onClick={() => setViewMode("timeline")}
        >
          타임라인
        </button>
        <button
          type="button"
          className={`board-detail-tab${viewMode === "calendar" ? " active" : ""}`}
          onClick={() => setViewMode("calendar")}
        >
          캘린더
        </button>
        <button
          type="button"
          className={`board-detail-tab${viewMode === "graph" ? " active" : ""}`}
          onClick={() => setViewMode("graph")}
        >
          그래프
        </button>
      </div>
      {selectedTabId && (
        <div className="tab-scope-bar">
          <span className="tab-scope-label">
            범위: <strong>{selectedTabName || "탭"}</strong>
          </span>
          {boardGroups
            .filter((g) => tabGroupIds.includes(g.id))
            .map((g) => (
              <span key={g.id} className="tab-scope-chip">
                {g.name}
                <button
                  type="button"
                  onClick={() =>
                    removeGroupFromTab.mutate({
                      tabId: selectedTabId,
                      groupId: g.id,
                    })
                  }
                  aria-label="범위에서 제거"
                >
                  ✕
                </button>
              </span>
            ))}
          {tabGroupIds.length === 0 && (
            <span className="tab-scope-empty">
              그룹을 추가하면 그 그룹의 카드만 표시됩니다
            </span>
          )}
          <select
            className="tab-scope-add"
            value=""
            onChange={(e) => {
              if (e.target.value)
                addGroupToTab.mutate({
                  tabId: selectedTabId,
                  groupId: e.target.value,
                });
            }}
          >
            <option value="">+ 그룹</option>
            {boardGroups
              .filter((g) => !tabGroupIds.includes(g.id))
              .map((g) => (
                <option key={g.id} value={g.id}>
                  {g.name}
                </option>
              ))}
          </select>
          <button
            type="button"
            className="tab-scope-clear"
            onClick={() => setSelectedTabId(null)}
          >
            전체 보기
          </button>
        </div>
      )}
      {viewMode === "timeline" ? (
        <>
          <div className="board-detail-toolbar">
            <button
              type="button"
              onClick={() => setTimelineStart((d) => addDays(d, -7))}
            >
              ‹
            </button>
            <h2 className="board-detail-month">
              {format(timelineStart, "MM-dd")} ~{" "}
              {format(addDays(timelineStart, TIMELINE_DAYS - 1), "MM-dd")}
            </h2>
            <button
              type="button"
              onClick={() => setTimelineStart((d) => addDays(d, 7))}
            >
              ›
            </button>
            <button
              type="button"
              className="board-detail-today"
              onClick={() => setTimelineStart(addDays(new Date(), -2))}
            >
              오늘
            </button>
          </div>
          {graph.isLoading ? (
            <p className="loading">불러오는 중...</p>
          ) : (
            <SwimlaneTimeline
              anchor={timelineStart}
              dayCount={TIMELINE_DAYS}
              tabs={tabs.data ?? []}
              groups={graph.data?.groups ?? []}
              tabGroups={graph.data?.tabGroups ?? {}}
              groupMembers={graph.data?.groupMembers ?? {}}
              cards={graph.data?.cards ?? []}
              cardRelations={graph.data?.cardRelations ?? []}
              onCardClick={(cardId) => setSelectedCardId(cardId)}
              onCreateCard={handleCreateCardInCell}
              onAddNextStep={handleAddNextStep}
            />
          )}
        </>
      ) : viewMode === "calendar" ? (
        <>
          <div className="board-detail-toolbar">
            <button
              type="button"
              onClick={() => setAnchor((d) => addMonths(d, -1))}
            >
              ‹
            </button>
            <h2 className="board-detail-month">
              {format(anchor, "yyyy년 M월")}
            </h2>
            <button
              type="button"
              onClick={() => setAnchor((d) => addMonths(d, 1))}
            >
              ›
            </button>
            <button
              type="button"
              className="board-detail-today"
              onClick={() => setAnchor(startOfMonth(new Date()))}
            >
              오늘
            </button>
          </div>
          {calendar.isLoading ? (
            <p className="loading">캘린더 불러오는 중...</p>
          ) : calendar.error ? (
            <p className="auth-error">
              캘린더 로드 실패: {String(calendar.error)}
            </p>
          ) : (
            <MonthGrid
              anchor={anchor}
              cards={
                scopedCardIds
                  ? (calendar.data?.cards ?? []).filter((c) =>
                      scopedCardIds.has(c.id),
                    )
                  : (calendar.data?.cards ?? [])
              }
              dateMemos={calendar.data?.dateMemos ?? []}
              onCardClick={(c) => setSelectedCardId(c.id)}
              onCellClick={(iso) => setCreateInitialDate(iso)}
              onCardReschedule={handleReschedule}
            />
          )}
        </>
      ) : graph.isLoading ? (
        <p className="loading">그래프 불러오는 중...</p>
      ) : graph.error ? (
        <p className="auth-error">그래프 로드 실패: {String(graph.error)}</p>
      ) : (
        <div className="board-detail-graph-wrap">
          <BoardGraph
            cards={graph.data?.cards ?? []}
            groups={graph.data?.groups ?? []}
            cardRelations={graph.data?.cardRelations ?? []}
            groupRelations={graph.data?.groupRelations ?? []}
            onCardClick={(cardId) => setSelectedCardId(cardId)}
            onCreateRelation={handleCreateRelation}
            onMoveCard={handleMoveCard}
            scopedCardIds={scopedCardIds}
          />
          <GroupManager
            boardId={boardId}
            cards={graph.data?.cards ?? []}
          />
        </div>
      )}
      <CardDialog
        open={createInitialDate !== null}
        title="새 카드"
        onClose={() => setCreateInitialDate(null)}
      >
        {createInitialDate !== null && (
          <CardForm
            initial={emptyCardForm(createInitialDate || undefined)}
            submitting={createCard.isPending}
            submitLabel="생성"
            onCancel={() => setCreateInitialDate(null)}
            onSubmit={handleCreate}
          />
        )}
      </CardDialog>
      </div>
      {selectedCardId ? (
        <CardInspector
          cardId={selectedCardId}
          boardId={boardId}
          onClose={() => setSelectedCardId(null)}
        />
      ) : selectedNoteId ? (
        <PerpetualNoteInspector
          boardId={boardId}
          noteId={selectedNoteId}
          onClose={() => setSelectedNoteId(null)}
        />
      ) : null}
    </div>
  );
}
