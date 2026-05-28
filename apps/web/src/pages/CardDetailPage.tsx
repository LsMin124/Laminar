import { useNavigate, useParams } from "react-router";
import { useCard, useCardRendered } from "../lib/queries";
import "./CardDetailPage.css";

export function CardDetailPage() {
  const params = useParams();
  const navigate = useNavigate();
  const boardId = params.boardId ?? "";
  const cardId = params.cardId ?? "";

  const card = useCard(cardId);
  const rendered = useCardRendered(cardId);

  if (card.isLoading) return <p className="loading">카드 불러오는 중...</p>;
  if (card.error || !card.data) {
    return (
      <div className="card-detail">
        <p className="auth-error">카드를 찾을 수 없습니다.</p>
        <button type="button" onClick={() => navigate(`/boards/${boardId}`)}>
          ← 보드로
        </button>
      </div>
    );
  }

  return (
    <div className="card-detail">
      <header className="card-detail-header">
        <button
          type="button"
          className="board-detail-back"
          onClick={() => navigate(`/boards/${boardId}`)}
        >
          ← 보드
        </button>
        <span className={`card-importance importance-${card.data.importance.toLowerCase()}`}>
          {card.data.importance}
        </span>
        {card.data.completed && <span className="card-status">완료</span>}
      </header>
      <h1 className="card-detail-title">{card.data.title}</h1>
      <dl className="card-detail-meta">
        {card.data.startDate && (
          <>
            <dt>기간</dt>
            <dd>
              {card.data.startDate}
              {card.data.endDate && card.data.endDate !== card.data.startDate
                ? ` ~ ${card.data.endDate}`
                : ""}
              {card.data.startTime && ` ${card.data.startTime}`}
            </dd>
          </>
        )}
        {card.data.rrule && (
          <>
            <dt>반복</dt>
            <dd>
              <code>{card.data.rrule}</code>
            </dd>
          </>
        )}
        {card.data.origin !== "MANUAL" && (
          <>
            <dt>출처</dt>
            <dd>{card.data.origin}</dd>
          </>
        )}
      </dl>
      <div className="card-detail-body">
        {rendered.data?.html ? (
          <div
            className="markdown"
            dangerouslySetInnerHTML={{ __html: rendered.data.html }}
          />
        ) : card.data.bodyMd ? (
          <pre className="markdown-raw">{card.data.bodyMd}</pre>
        ) : (
          <p className="markdown-empty">내용 없음</p>
        )}
      </div>
    </div>
  );
}
