import { useState } from "react";
import { useNavigate } from "react-router";
import {
  useAdminAllBoards,
  useAdminBoardCards,
  useAuditLogs,
  useRevealCardBody,
} from "../lib/queries";
import "./AdminPage.css";

export function AdminPage() {
  const navigate = useNavigate();
  const boards = useAdminAllBoards();
  const audit = useAuditLogs(100);
  const [selectedBoardId, setSelectedBoardId] = useState<string | null>(null);
  const adminCards = useAdminBoardCards(selectedBoardId);
  const reveal = useRevealCardBody();
  const [revealed, setRevealed] = useState<{
    cardId: string;
    title: string;
    bodyMd: string | null;
  } | null>(null);

  async function handleReveal(cardId: string) {
    const reason = prompt("본문 노출 사유 (감사 로그에 기록됨, 10자 이상)");
    if (!reason || reason.length < 10) {
      alert("사유는 10자 이상이어야 합니다.");
      return;
    }
    try {
      const res = await reveal.mutateAsync({ cardId, reason });
      setRevealed({
        cardId: res.cardId,
        title: res.title,
        bodyMd: res.bodyMd,
      });
    } catch (e) {
      alert(
        `노출 실패: ${e instanceof Error ? e.message : String(e)} (OWNER만 가능)`,
      );
    }
  }

  return (
    <div className="admin-page">
      <header className="admin-header">
        <button
          type="button"
          className="board-detail-back"
          onClick={() => navigate("/")}
        >
          ← 보드 목록
        </button>
        <h1>운영 콘솔</h1>
        <span className="admin-warning">OWNER 전용</span>
      </header>

      <p className="admin-disclaimer">
        ⚠️ 이 페이지의 모든 작업은 감사 로그에 기록됩니다. 카드 본문 노출은
        고위험 행위 (severity=high)로 분류됩니다.
      </p>

      <section className="admin-section">
        <h2>워크스페이스 전체 보드 ({boards.data?.length ?? 0})</h2>
        {boards.error && (
          <p className="auth-error">
            접근 실패 — OWNER 권한이 필요합니다.
          </p>
        )}
        {boards.data && (
          <ul className="admin-board-list">
            {boards.data.map((b) => (
              <li
                key={b.id}
                className={`admin-board-item${selectedBoardId === b.id ? " selected" : ""}`}
                onClick={() =>
                  setSelectedBoardId(selectedBoardId === b.id ? null : b.id)
                }
              >
                <span className="admin-board-name">{b.name}</span>
                <span className="admin-board-slug">/{b.slug}</span>
                <code className="admin-board-user">
                  user: {b.userId.slice(0, 8)}...
                </code>
              </li>
            ))}
          </ul>
        )}
      </section>

      {selectedBoardId && (
        <section className="admin-section">
          <h2>카드 메타 (body 제외, {adminCards.data?.length ?? 0})</h2>
          {adminCards.data && (
            <ul className="admin-card-list">
              {adminCards.data.map((card, idx) => {
                const cardId =
                  (card.cardId as string | undefined) ??
                  (card.id as string | undefined) ??
                  "";
                return (
                  <li key={cardId || idx} className="admin-card-item">
                    <div className="admin-card-row">
                      <span className="admin-card-title">
                        {(card.title as string) ?? "(제목 없음)"}
                      </span>
                      {card.importance && (
                        <span className="admin-card-importance">
                          {String(card.importance)}
                        </span>
                      )}
                    </div>
                    {card.startDate && (
                      <div className="admin-card-meta">
                        📅 {String(card.startDate)}
                        {card.endDate && card.endDate !== card.startDate
                          ? ` ~ ${card.endDate}`
                          : ""}
                      </div>
                    )}
                    <code className="admin-card-id">
                      card: {cardId.slice(0, 8)}...
                    </code>
                    <button
                      type="button"
                      className="admin-reveal-btn"
                      onClick={() => handleReveal(cardId)}
                      disabled={reveal.isPending}
                    >
                      🔓 본문 노출 (escape hatch)
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </section>
      )}

      {revealed && (
        <section className="admin-section admin-revealed">
          <header className="admin-revealed-head">
            <h2>🔓 노출된 본문</h2>
            <button type="button" onClick={() => setRevealed(null)}>
              닫기
            </button>
          </header>
          <h3>{revealed.title}</h3>
          <pre className="admin-revealed-body">
            {revealed.bodyMd ?? "(빈 본문)"}
          </pre>
        </section>
      )}

      <section className="admin-section">
        <h2>최근 감사 로그 ({audit.data?.length ?? 0})</h2>
        {audit.isLoading ? (
          <p className="loading">불러오는 중...</p>
        ) : (
          <ul className="audit-list">
            {audit.data?.map((log) => (
              <li
                key={log.id}
                className={`audit-item severity-${String(log.payload?.severity ?? "info")}`}
              >
                <div className="audit-line">
                  <code className="audit-action">{log.action}</code>
                  <span className="audit-target">
                    {log.targetType} {log.targetId?.slice(0, 8) ?? "—"}
                  </span>
                  <time className="audit-time">
                    {new Date(log.occurredAt).toLocaleString()}
                  </time>
                </div>
                {log.summary && (
                  <div className="audit-summary">{log.summary}</div>
                )}
                {log.payload && Object.keys(log.payload).length > 0 && (
                  <pre className="audit-payload">
                    {JSON.stringify(log.payload, null, 2)}
                  </pre>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
