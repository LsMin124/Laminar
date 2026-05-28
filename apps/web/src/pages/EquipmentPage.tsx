import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router";
import {
  useCreateEquipment,
  useDeleteEquipment,
  useEquipmentList,
  useMyReservations,
  useToggleEquipmentActive,
} from "../lib/queries";
import "./EquipmentPage.css";

export function EquipmentPage() {
  const navigate = useNavigate();
  const equipment = useEquipmentList(false);
  const myReservations = useMyReservations();
  const createEquipment = useCreateEquipment();
  const toggleActive = useToggleEquipmentActive();
  const deleteEquipment = useDeleteEquipment();

  const [showCreate, setShowCreate] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [location, setLocation] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      await createEquipment.mutateAsync({
        name,
        description: description || undefined,
        location: location || undefined,
      });
      setName("");
      setDescription("");
      setLocation("");
      setShowCreate(false);
    } catch (e) {
      setError(`등록 실패: ${e instanceof Error ? e.message : String(e)}`);
    }
  }

  return (
    <div className="equipment-page">
      <header className="equipment-header">
        <button
          type="button"
          className="board-detail-back"
          onClick={() => navigate("/")}
        >
          ← 보드 목록
        </button>
        <h1>공용 자원</h1>
      </header>

      <section className="equipment-section">
        <header className="equipment-section-head">
          <h2>장비 ({equipment.data?.length ?? 0})</h2>
          <button
            type="button"
            className="primary"
            onClick={() => setShowCreate((v) => !v)}
          >
            {showCreate ? "취소" : "+ 새 장비"}
          </button>
        </header>

        {showCreate && (
          <form className="equipment-create-form" onSubmit={handleCreate}>
            <input
              type="text"
              placeholder="장비명"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              maxLength={200}
            />
            <input
              type="text"
              placeholder="위치"
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              maxLength={200}
            />
            <textarea
              placeholder="설명"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              maxLength={2000}
              rows={2}
            />
            <button
              type="submit"
              className="primary"
              disabled={createEquipment.isPending}
            >
              {createEquipment.isPending ? "등록 중..." : "등록"}
            </button>
            {error && <p className="auth-error">{error}</p>}
          </form>
        )}

        {equipment.isLoading ? (
          <p className="loading">불러오는 중...</p>
        ) : (
          <ul className="equipment-list">
            {equipment.data?.map((eq) => (
              <li
                key={eq.id}
                className={`equipment-card${eq.active ? "" : " inactive"}`}
              >
                <div
                  className="equipment-card-main"
                  onClick={() => navigate(`/equipment/${eq.id}`)}
                  role="button"
                  tabIndex={0}
                >
                  <div className="equipment-card-name">
                    {eq.name}
                    {!eq.active && <span className="badge-inactive">비활성</span>}
                  </div>
                  {eq.location && (
                    <div className="equipment-card-meta">📍 {eq.location}</div>
                  )}
                  {eq.description && (
                    <div className="equipment-card-desc">{eq.description}</div>
                  )}
                </div>
                <div className="equipment-card-actions">
                  <button
                    type="button"
                    onClick={() =>
                      toggleActive.mutate({
                        equipmentId: eq.id,
                        active: !eq.active,
                      })
                    }
                  >
                    {eq.active ? "비활성화" : "활성화"}
                  </button>
                  <button
                    type="button"
                    className="danger"
                    onClick={() => {
                      if (confirm(`'${eq.name}' 장비를 삭제할까요?`)) {
                        deleteEquipment.mutate(eq.id);
                      }
                    }}
                  >
                    삭제
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="equipment-section">
        <h2>내 예약 ({myReservations.data?.length ?? 0})</h2>
        {myReservations.data && myReservations.data.length > 0 ? (
          <ul className="reservation-list">
            {myReservations.data.map((r) => (
              <li key={r.id} className="reservation-item">
                <div className="reservation-time">
                  {new Date(r.startAt).toLocaleString()} →{" "}
                  {new Date(r.endAt).toLocaleString()}
                </div>
                {r.purpose && (
                  <div className="reservation-purpose">{r.purpose}</div>
                )}
                {r.rrule && (
                  <code className="reservation-rrule">{r.rrule}</code>
                )}
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
