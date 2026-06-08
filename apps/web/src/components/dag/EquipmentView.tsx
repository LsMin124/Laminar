import { useMemo, useState } from "react";
import { ApiError } from "../../lib/api";
import {
  useCreateEquipment,
  useDeleteEquipment,
  useEquipment,
  useToggleEquipmentActive,
  useUpdateEquipment,
  type Equipment,
} from "../../lib/equipment";
import { useDialogs } from "../ui/DialogProvider";
import "./EquipmentView.css";

interface FormState {
  id?: string;
  name: string;
  location: string;
  description: string;
}

/**
 * 장비 관리 전용 화면 — 주제(워크스페이스) 공유 장비 레지스트리(목록/등록/수정/활성토글/삭제).
 * 좌측 레일 '장' 타일에서 진입. 예약·로그·공용 캘린더는 다음 증분.
 */
export function EquipmentView({
  subjectName,
  onClose,
}: {
  subjectName: string;
  onClose: () => void;
}) {
  const equipment = useEquipment();
  const createEquipment = useCreateEquipment();
  const updateEquipment = useUpdateEquipment();
  const toggleActive = useToggleEquipmentActive();
  const deleteEquipment = useDeleteEquipment();
  const dialogs = useDialogs();

  const [showInactive, setShowInactive] = useState(false);
  const [form, setForm] = useState<FormState | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const all = useMemo(() => equipment.data ?? [], [equipment.data]);
  const visible = showInactive ? all : all.filter((e) => e.active);
  const inactiveCount = all.filter((e) => !e.active).length;

  function openNew() {
    setFormError(null);
    setForm({ name: "", location: "", description: "" });
  }
  function openEdit(e: Equipment) {
    setFormError(null);
    setForm({
      id: e.id,
      name: e.name,
      location: e.location ?? "",
      description: e.description ?? "",
    });
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!form || !form.name.trim()) return;
    const payload = {
      name: form.name.trim(),
      location: form.location.trim() || null,
      description: form.description.trim() || null,
    };
    setSaving(true);
    setFormError(null);
    try {
      if (form.id) {
        await updateEquipment.mutateAsync({ id: form.id, ...payload });
      } else {
        await createEquipment.mutateAsync(payload);
      }
      setForm(null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setFormError("같은 이름의 장비가 이미 있습니다.");
      } else {
        setFormError("저장에 실패했습니다. 다시 시도해 주세요.");
      }
    } finally {
      setSaving(false);
    }
  }

  async function onDelete(e: Equipment) {
    const ok = await dialogs.confirm({
      title: "장비 삭제",
      message: `"${e.name}"을(를) 삭제할까요? (소유자만 삭제할 수 있습니다)`,
      confirmLabel: "삭제",
      danger: true,
    });
    if (!ok) return;
    try {
      await deleteEquipment.mutateAsync(e.id);
    } catch (err) {
      const forbidden = err instanceof ApiError && err.status === 403;
      await dialogs.alert({
        title: "삭제 불가",
        message: forbidden ? "소유자만 장비를 삭제할 수 있습니다." : "삭제에 실패했습니다.",
      });
    }
  }

  return (
    <div className="eq">
      <header className="eq-head">
        <div className="eq-title">
          <span className="eq-kicker">장비 관리</span>
          <h1 className="eq-h1">{subjectName}</h1>
        </div>
        <div className="eq-head-actions">
          <label className="eq-toggle">
            <input
              type="checkbox"
              checked={showInactive}
              onChange={(ev) => setShowInactive(ev.target.checked)}
            />
            비활성 포함{inactiveCount > 0 ? ` (${inactiveCount})` : ""}
          </label>
          <button type="button" className="eq-add" onClick={openNew}>
            ＋ 장비 등록
          </button>
          <button type="button" className="eq-close" onClick={onClose} title="보드로 돌아가기">
            ✕ 닫기
          </button>
        </div>
      </header>

      <div className="eq-body">
        {equipment.isLoading ? (
          <div className="eq-msg">불러오는 중...</div>
        ) : equipment.isError ? (
          <div className="eq-msg">장비를 불러오지 못했습니다.</div>
        ) : visible.length === 0 ? (
          <div className="eq-empty">
            <p>{all.length === 0 ? "등록된 장비가 없습니다." : "활성 장비가 없습니다."}</p>
            <button type="button" className="eq-add" onClick={openNew}>
              ＋ 첫 장비 등록
            </button>
          </div>
        ) : (
          <ul className="eq-list">
            {visible.map((e) => (
              <li key={e.id} className={`eq-card${e.active ? "" : " inactive"}`}>
                <div className="eq-card-main">
                  <div className="eq-card-headline">
                    <h2 className="eq-name">{e.name}</h2>
                    {!e.active && <span className="eq-badge">비활성</span>}
                    {e.location && <span className="eq-loc">⌖ {e.location}</span>}
                  </div>
                  {e.description && <p className="eq-desc">{e.description}</p>}
                </div>
                <div className="eq-card-acts">
                  <button type="button" className="eq-act" onClick={() => openEdit(e)}>
                    수정
                  </button>
                  <button
                    type="button"
                    className="eq-act"
                    onClick={() => toggleActive.mutate({ id: e.id, active: !e.active })}
                  >
                    {e.active ? "비활성화" : "활성화"}
                  </button>
                  <button type="button" className="eq-act danger" onClick={() => onDelete(e)}>
                    삭제
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {form && (
        <div className="eq-overlay" onClick={() => setForm(null)}>
          <form className="eq-form" onClick={(ev) => ev.stopPropagation()} onSubmit={onSubmit}>
            <header className="eq-form-head">{form.id ? "장비 수정" : "새 장비 등록"}</header>
            <label className="eq-field">
              <span>
                이름 <em className="req">*</em>
              </span>
              <input
                className="eq-input"
                value={form.name}
                onChange={(ev) => setForm({ ...form, name: ev.target.value })}
                placeholder="예: 공초점 현미경 A"
                autoFocus
                maxLength={200}
              />
            </label>
            <label className="eq-field">
              <span>위치</span>
              <input
                className="eq-input"
                value={form.location}
                onChange={(ev) => setForm({ ...form, location: ev.target.value })}
                placeholder="예: 3층 영상실"
                maxLength={200}
              />
            </label>
            <label className="eq-field">
              <span>설명</span>
              <textarea
                className="eq-input eq-textarea"
                value={form.description}
                onChange={(ev) => setForm({ ...form, description: ev.target.value })}
                placeholder="사용 조건·주의사항 등"
                rows={3}
                maxLength={2000}
              />
            </label>
            {formError && <div className="eq-form-err">{formError}</div>}
            <footer className="eq-form-foot">
              <button type="button" className="eq-cancel" onClick={() => setForm(null)}>
                취소
              </button>
              <button type="submit" className="eq-submit" disabled={!form.name.trim() || saving}>
                {saving ? "저장 중..." : "저장"}
              </button>
            </footer>
          </form>
        </div>
      )}
    </div>
  );
}
