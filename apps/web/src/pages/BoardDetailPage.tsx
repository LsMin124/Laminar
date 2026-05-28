import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { addMonths, format, startOfMonth, endOfMonth } from "date-fns";
import { MonthGrid } from "../components/calendar/MonthGrid";
import { useBoard, useBoardCalendar } from "../lib/queries";
import "./BoardDetailPage.css";

export function BoardDetailPage() {
  const params = useParams();
  const navigate = useNavigate();
  const boardId = params.boardId ?? "";
  const [anchor, setAnchor] = useState<Date>(() => startOfMonth(new Date()));

  const board = useBoard(boardId);
  const range = useMemo(() => {
    const from = startOfMonth(anchor);
    const to = endOfMonth(anchor);
    return {
      from: format(from, "yyyy-MM-dd"),
      to: format(to, "yyyy-MM-dd"),
    };
  }, [anchor]);
  const calendar = useBoardCalendar(boardId, range.from, range.to);

  return (
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
      </header>
      <div className="board-detail-toolbar">
        <button
          type="button"
          onClick={() => setAnchor((d) => addMonths(d, -1))}
        >
          ‹
        </button>
        <h2 className="board-detail-month">{format(anchor, "yyyy년 M월")}</h2>
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
          onCardClick={(c) => navigate(`/boards/${boardId}/cards/${c.id}`)}
        />
      )}
    </div>
  );
}
