import { useMemo, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router";
import {
  useCancelReservation,
  useCreateReservation,
  useEquipment,
  useEquipmentReservations,
} from "../lib/queries";
import "./EquipmentDetailPage.css";

function toIsoLocal(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
    date.getDate(),
  )}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function EquipmentDetailPage() {
  const navigate = useNavigate();
  const params = useParams();
  const equipmentId = params.equipmentId ?? "";

  const equipment = useEquipment(equipmentId);

  const range = useMemo(() => {
    const now = new Date();
    const start = new Date(now);
    start.setDate(start.getDate() - 7);
    start.setHours(0, 0, 0, 0);
    const end = new Date(now);
    end.setDate(end.getDate() + 30);
    end.setHours(23, 59, 59, 0);
    return {
      from: start.toISOString(),
      to: end.toISOString(),
    };
  }, []);

  const reservations = useEquipmentReservations(
    equipmentId,
    range.from,
    range.to,
  );
  const createReservation = useCreateReservation(equipmentId);
  const cancelReservation = useCancelReservation(equipmentId);

  const [startAt, setStartAt] = useState(() => {
    const now = new Date();
    now.setMinutes(0, 0, 0);
    now.setHours(now.getHours() + 1);
    return toIsoLocal(now);
  });
  const [endAt, setEndAt] = useState(() => {
    const now = new Date();
    now.setMinutes(0, 0, 0);
    now.setHours(now.getHours() + 2);
    return toIsoLocal(now);
  });
  const [purpose, setPurpose] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function handleReserve(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      const start = new Date(startAt);
      const end = new Date(endAt);
      if (!(end > start)) {
        setError("종료 시각은 시작 시각보다 뒤여야 합니다.");
        return;
      }
      await createReservation.mutateAsync({
        startAt: start.toISOString(),
        endAt: end.toISOString(),
        purpose: purpose || undefined,
      });
      setPurpose("");
    } catch (e) {
      setError(`예약 실패: ${e instanceof Error ? e.message : String(e)}`);
    }
  }

  return (
    <div className="equipment-detail">
      <header className="equipment-header">
        <button
          type="button"
          className="board-detail-back"
          onClick={() => navigate("/equipment")}
        >
          ← 장비 목록
        </button>
        <h1>{equipment.data?.name ?? "불러오는 중..."}</h1>
        {equipment.data && !equipment.data.active && (
          <span className="badge-inactive">비활성</span>
        )}
      </header>

      {equipment.data?.description && (
        <p className="equipment-detail-desc">{equipment.data.description}</p>
      )}
      {equipment.data?.location && (
        <p className="equipment-detail-meta">📍 {equipment.data.location}</p>
      )}

      <section className="equipment-section">
        <h2>예약 신청</h2>
        <form className="reserve-form" onSubmit={handleReserve}>
          <label>
            <span>시작</span>
            <input
              type="datetime-local"
              value={startAt}
              onChange={(e) => setStartAt(e.target.value)}
              required
            />
          </label>
          <label>
            <span>종료</span>
            <input
              type="datetime-local"
              value={endAt}
              onChange={(e) => setEndAt(e.target.value)}
              required
            />
          </label>
          <label className="reserve-purpose">
            <span>목적</span>
            <input
              type="text"
              value={purpose}
              onChange={(e) => setPurpose(e.target.value)}
              maxLength={500}
            />
          </label>
          <button
            type="submit"
            className="primary"
            disabled={createReservation.isPending || !equipment.data?.active}
          >
            {createReservation.isPending ? "예약 중..." : "예약"}
          </button>
          {error && <p className="auth-error">{error}</p>}
        </form>
      </section>

      <section className="equipment-section">
        <h2>예약 현황 (지난 7일 ~ 30일)</h2>
        {reservations.isLoading ? (
          <p className="loading">불러오는 중...</p>
        ) : reservations.data && reservations.data.length > 0 ? (
          <ul className="reservation-list">
            {reservations.data
              .slice()
              .sort((a, b) => (a.startAt < b.startAt ? -1 : 1))
              .map((r) => (
                <li key={r.id} className="reservation-item">
                  <div className="reservation-time">
                    {new Date(r.startAt).toLocaleString()} →{" "}
                    {new Date(r.endAt).toLocaleString()}
                  </div>
                  {r.purpose && (
                    <div className="reservation-purpose">{r.purpose}</div>
                  )}
                  <div className="reservation-actions">
                    <button
                      type="button"
                      className="danger"
                      onClick={() => {
                        if (confirm("예약을 취소할까요?")) {
                          cancelReservation.mutate(r.id);
                        }
                      }}
                    >
                      취소
                    </button>
                  </div>
                </li>
              ))}
          </ul>
        ) : (
          <p className="markdown-empty">예약 없음</p>
        )}
      </section>
    </div>
  );
}
