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
import type { CardResponse } from "../lib/types";
import { MonthGrid } from "../components/calendar/MonthGrid";
import { BoardGraph } from "../components/graph/BoardGraph";
import { GroupManager } from "../components/group/GroupManager";
import {
  CardForm,
  emptyCardForm,
  type CardFormValues,
} from "../components/card/CardForm";
import { CardDialog } from "../components/card/CardDialog";
import { CardInspector } from "../components/card/CardInspector";
import {
  useBoard,
  useBoardCalendar,
  useBoardGraph,
  useCreateCard,
  useCreateCardRelation,
  useRescheduleCard,
} from "../lib/queries";
import "./BoardDetailPage.css";

type ViewMode = "calendar" | "graph";

export function BoardDetailPage() {
  const params = useParams();
  const navigate = useNavigate();
  const boardId = params.boardId ?? "";
  const [anchor, setAnchor] = useState<Date>(() => startOfMonth(new Date()));
  const [viewMode, setViewMode] = useState<ViewMode>("calendar");
  const [createInitialDate, setCreateInitialDate] = useState<string | null>(
    null,
  );
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null);

  const board = useBoard(boardId);
  const graph = useBoardGraph(viewMode === "graph" ? boardId : null);
  const range = useMemo(() => {
    const from = startOfMonth(anchor);
    const to = endOfMonth(anchor);
    return {
      from: format(from, "yyyy-MM-dd"),
      to: format(to, "yyyy-MM-dd"),
    };
  }, [anchor]);
  const calendar = useBoardCalendar(boardId, range.from, range.to);
  const createCard = useCreateCard(boardId);
  const reschedule = useRescheduleCard(boardId);
  const createRelation = useCreateCardRelation(boardId);

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

  return (
    <div className="board-workspace">
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
      {viewMode === "calendar" ? (
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
              cards={calendar.data?.cards ?? []}
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
            onCreateRelation={(fromCardId, toCardId) =>
              createRelation.mutate({ fromCardId, toCardId })
            }
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
      {selectedCardId && (
        <CardInspector
          cardId={selectedCardId}
          boardId={boardId}
          onClose={() => setSelectedCardId(null)}
        />
      )}
    </div>
  );
}
