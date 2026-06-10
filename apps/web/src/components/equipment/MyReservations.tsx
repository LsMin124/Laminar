import { useMemo } from "react";
import type { Equipment, Reservation } from "../../lib/equipmentTypes";
import { useCancelReservation, useMyReservations } from "../../lib/reservations";
import { useDialogs } from "../ui/DialogProvider";
import { LinkedCardChip } from "./CardPicker";
import { fmtRange } from "./EquipmentReservations";

/** 내 예약 통합뷰 — 모든 장비에 걸친 내 예약(시작순), 장비명 표시 + 취소. */
export function MyReservations({ equipment }: { equipment: Equipment[] }) {
  const dialogs = useDialogs();
  const mine = useMyReservations();
  const cancelResv = useCancelReservation();

  const nameOf = useMemo(() => {
    const map = new Map(equipment.map((e) => [e.id, e.name]));
    return (id: string) => map.get(id) ?? "(삭제된 장비)";
  }, [equipment]);

  const sorted = useMemo(
    () => [...(mine.data ?? [])].sort((a, b) => a.startAt.localeCompare(b.startAt)),
    [mine.data],
  );
  // past 분류 기준 시각 — 렌더 순수성을 위해 Date.now() 대신 마지막 fetch 시각(refetch 시 갱신).
  const now = mine.dataUpdatedAt;

  async function onCancel(r: Reservation) {
    const ok = await dialogs.confirm({
      title: "예약 취소",
      message: "이 예약을 취소할까요?",
      confirmLabel: "취소",
      danger: true,
    });
    if (!ok) return;
    try {
      await cancelResv.mutateAsync(r.id);
    } catch {
      await dialogs.alert({ title: "취소 불가", message: "예약을 취소하지 못했습니다." });
    }
  }

  if (mine.isLoading) return <div className="eq-msg">불러오는 중...</div>;
  if (mine.isError) return <div className="eq-msg">예약을 불러오지 못했습니다.</div>;
  if (sorted.length === 0)
    return (
      <div className="eq-empty">
        <p>내 예약이 없습니다.</p>
      </div>
    );

  return (
    <ul className="eq-resv-list">
      {sorted.map((r) => {
        const past = new Date(r.endAt).getTime() < now;
        return (
          <li key={r.id} className={`eq-resv${past ? " past" : ""}`}>
            <span className="eq-resv-range">{fmtRange(r.startAt, r.endAt)}</span>
            <span className="eq-resv-equip">{nameOf(r.equipmentId)}</span>
            <span className="eq-resv-purpose">{r.purpose || "—"}</span>
            {r.cardId && <LinkedCardChip cardId={r.cardId} />}
            {past ? (
              <span className="eq-resv-tag">종료</span>
            ) : (
              <button type="button" className="eq-act danger" onClick={() => onCancel(r)}>
                취소
              </button>
            )}
          </li>
        );
      })}
    </ul>
  );
}
