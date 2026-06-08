import { useMemo, useState } from "react";
import { ApiError } from "../../lib/api";
import {
  useCancelReservation,
  useCreateReservation,
  useReservations,
  type Equipment,
  type Reservation,
} from "../../lib/equipment";
import { useDialogs } from "../ui/DialogProvider";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];
const DAY_MS = 86400000;

// datetime-local 값("YYYY-MM-DDTHH:MM", 브라우저 로컬) → ISO(UTC Z). 백엔드 OffsetDateTime이 파싱.
function localToIso(local: string): string {
  return new Date(local).toISOString();
}
function toLocalInput(d: Date): string {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(
    d.getMinutes(),
  )}`;
}
function fmtRange(startIso: string, endIso: string): string {
  const s = new Date(startIso);
  const e = new Date(endIso);
  const d = (x: Date) => `${x.getMonth() + 1}/${x.getDate()}(${WEEKDAYS[x.getDay()]})`;
  const t = (x: Date) =>
    `${String(x.getHours()).padStart(2, "0")}:${String(x.getMinutes()).padStart(2, "0")}`;
  const sameDay = s.toDateString() === e.toDateString();
  return sameDay ? `${d(s)} ${t(s)} – ${t(e)}` : `${d(s)} ${t(s)} – ${d(e)} ${t(e)}`;
}

interface ResvForm {
  startLocal: string;
  endLocal: string;
  purpose: string;
}

/**
 * 장비 예약 서브뷰 — 장비 선택 + 해당 장비의 예약 목록(최근~향후 90일) + 예약 생성/취소.
 * 시간 겹침 차단은 백엔드(409), 시작<종료·최대 7일은 클라+백엔드 양쪽 검증.
 */
export function EquipmentReservations({
  equipment,
  selectedId,
  onSelect,
}: {
  equipment: Equipment[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}) {
  const dialogs = useDialogs();
  const createResv = useCreateReservation();
  const cancelResv = useCancelReservation();

  // 조회 범위는 마운트 시 1회 고정(쿼리 키 안정화). now-1일 ~ now+90일.
  const range = useMemo(() => {
    const now = Date.now();
    return {
      from: new Date(now - DAY_MS).toISOString(),
      to: new Date(now + 90 * DAY_MS).toISOString(),
    };
  }, []);

  const selected = selectedId ?? equipment[0]?.id ?? null;
  const resv = useReservations(selected, range.from, range.to);
  const selectedName = equipment.find((e) => e.id === selected)?.name ?? "";

  const [form, setForm] = useState<ResvForm | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const sorted = useMemo(
    () => [...(resv.data ?? [])].sort((a, b) => a.startAt.localeCompare(b.startAt)),
    [resv.data],
  );
  const now = Date.now();

  function openForm() {
    const start = new Date();
    start.setMinutes(0, 0, 0);
    start.setHours(start.getHours() + 1);
    const end = new Date(start.getTime() + 3600000);
    setFormError(null);
    setForm({ startLocal: toLocalInput(start), endLocal: toLocalInput(end), purpose: "" });
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!form || !selected) return;
    const startMs = new Date(form.startLocal).getTime();
    const endMs = new Date(form.endLocal).getTime();
    if (!form.startLocal || !form.endLocal || Number.isNaN(startMs) || Number.isNaN(endMs)) {
      setFormError("시작·종료 시간을 입력하세요.");
      return;
    }
    if (endMs <= startMs) {
      setFormError("종료는 시작보다 뒤여야 합니다.");
      return;
    }
    if (endMs - startMs > 7 * DAY_MS) {
      setFormError("예약은 최대 7일까지입니다.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      await createResv.mutateAsync({
        equipmentId: selected,
        startAt: localToIso(form.startLocal),
        endAt: localToIso(form.endLocal),
        purpose: form.purpose.trim() || null,
      });
      setForm(null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setFormError("이미 예약된 시간과 겹칩니다.");
      } else if (err instanceof ApiError && err.status === 400) {
        setFormError("예약 시간이 올바르지 않습니다 (시작 < 종료, 최대 7일).");
      } else {
        setFormError("예약에 실패했습니다.");
      }
    } finally {
      setSaving(false);
    }
  }

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

  if (equipment.length === 0) {
    return (
      <div className="eq-empty">
        <p>예약할 활성 장비가 없습니다. 먼저 장비를 등록하세요.</p>
      </div>
    );
  }

  return (
    <div className="eq-resv-view">
      <div className="eq-resv-bar">
        <label className="eq-resv-pick">
          장비
          <select
            className="eq-input"
            value={selected ?? ""}
            onChange={(e) => onSelect(e.target.value)}
          >
            {equipment.map((e) => (
              <option key={e.id} value={e.id}>
                {e.name}
              </option>
            ))}
          </select>
        </label>
        <button type="button" className="eq-add" onClick={openForm}>
          ＋ 예약
        </button>
      </div>

      {resv.isLoading ? (
        <div className="eq-msg">불러오는 중...</div>
      ) : resv.isError ? (
        <div className="eq-msg">예약을 불러오지 못했습니다.</div>
      ) : sorted.length === 0 ? (
        <div className="eq-empty">
          <p>예약이 없습니다.</p>
          <button type="button" className="eq-add" onClick={openForm}>
            ＋ 첫 예약
          </button>
        </div>
      ) : (
        <ul className="eq-resv-list">
          {sorted.map((r) => {
            const past = new Date(r.endAt).getTime() < now;
            return (
              <li key={r.id} className={`eq-resv${past ? " past" : ""}`}>
                <span className="eq-resv-range">{fmtRange(r.startAt, r.endAt)}</span>
                <span className="eq-resv-purpose">{r.purpose || "—"}</span>
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
      )}

      {form && (
        <div className="eq-overlay" onClick={() => setForm(null)}>
          <form className="eq-form" onClick={(e) => e.stopPropagation()} onSubmit={onSubmit}>
            <header className="eq-form-head">새 예약 · {selectedName}</header>
            <label className="eq-field">
              <span>
                시작 <em className="req">*</em>
              </span>
              <input
                type="datetime-local"
                className="eq-input"
                value={form.startLocal}
                onChange={(e) => setForm({ ...form, startLocal: e.target.value })}
                autoFocus
              />
            </label>
            <label className="eq-field">
              <span>
                종료 <em className="req">*</em>
              </span>
              <input
                type="datetime-local"
                className="eq-input"
                value={form.endLocal}
                onChange={(e) => setForm({ ...form, endLocal: e.target.value })}
              />
            </label>
            <label className="eq-field">
              <span>목적</span>
              <input
                className="eq-input"
                value={form.purpose}
                onChange={(e) => setForm({ ...form, purpose: e.target.value })}
                placeholder="예: 샘플 이미징"
                maxLength={500}
              />
            </label>
            {formError && <div className="eq-form-err">{formError}</div>}
            <footer className="eq-form-foot">
              <button type="button" className="eq-cancel" onClick={() => setForm(null)}>
                취소
              </button>
              <button type="submit" className="eq-submit" disabled={saving}>
                {saving ? "예약 중..." : "예약"}
              </button>
            </footer>
          </form>
        </div>
      )}
    </div>
  );
}
