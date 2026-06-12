import { useMemo, useState } from "react";
import { ApiError } from "../../lib/api";
import { useCreateLog, useCreateLogColumn, useDeleteLogColumn, useLogColumns, useLogs } from "../../lib/equipmentLogs";
import type { Equipment, LogColumn, LogColumnType } from "../../lib/equipmentTypes";
import { useDialogs } from "../ui/DialogProvider";
import { localToIso, toLocalInput } from "./EquipmentReservations";

const TYPE_LABEL: Record<LogColumnType, string> = {
  TEXT: "텍스트",
  NUMBER: "숫자",
  ENUM: "선택",
  BOOL: "예/아니오",
  DATETIME: "일시",
};

function fmtDateTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getMonth() + 1}/${d.getDate()} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

function renderCell(col: LogColumn, raw: unknown): string {
  if (raw === undefined || raw === null || raw === "") return "—";
  const v = String(raw);
  if (col.columnType === "BOOL") return v === "true" ? "✓" : "—";
  if (col.columnType === "DATETIME") return fmtDateTime(v);
  return v;
}

interface EntryForm {
  loggedAtLocal: string;
  values: Record<string, string>;
  notes: string;
}
interface ColForm {
  columnLabel: string;
  columnType: LogColumnType;
  required: boolean;
  enumText: string;
  defaultValue: string;
}

/**
 * 장비 로그 시트 — 동적 컬럼 정의(관리) + 행 기록 + 테이블. 컬럼 type별 입력/렌더.
 * 시각(loggedAt·DATETIME 값)은 브라우저 로컬↔ISO 변환.
 * 기록은 MEMBER+, 컬럼 관리는 ADMIN+(§1.3 — isAdmin이 ⚙ 진입을 게이팅).
 */
export function EquipmentLogs({
  equipment,
  selectedId,
  onSelect,
  isAdmin,
}: {
  equipment: Equipment[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  isAdmin: boolean;
}) {
  const dialogs = useDialogs();
  const selected = selectedId ?? equipment[0]?.id ?? null;
  const columnsQ = useLogColumns(selected);
  const logsQ = useLogs(selected);
  const createColumn = useCreateLogColumn(selected ?? "");
  const deleteColumn = useDeleteLogColumn(selected ?? "");
  const createLog = useCreateLog(selected ?? "");

  const [manageCols, setManageCols] = useState(false);
  const [colForm, setColForm] = useState<ColForm | null>(null);
  const [entry, setEntry] = useState<EntryForm | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const columns = useMemo(
    () => [...(columnsQ.data ?? [])].sort((a, b) => a.priority - b.priority),
    [columnsQ.data],
  );
  const logs = logsQ.data ?? [];

  function openEntry() {
    if (!selected) return;
    const values: Record<string, string> = {};
    for (const c of columns) {
      values[c.columnKey] = c.columnType === "BOOL" ? "false" : (c.defaultValue ?? "");
    }
    setError(null);
    setEntry({ loggedAtLocal: toLocalInput(new Date()), values, notes: "" });
  }

  function setVal(key: string, v: string) {
    setEntry((f) => (f ? { ...f, values: { ...f.values, [key]: v } } : f));
  }

  async function submitEntry(e: React.FormEvent) {
    e.preventDefault();
    if (!entry || !selected) return;
    const values: Record<string, string> = {};
    for (const col of columns) {
      const raw = entry.values[col.columnKey] ?? "";
      if (col.columnType === "BOOL") {
        values[col.columnKey] = raw === "true" ? "true" : "false";
        continue;
      }
      if (raw === "") continue;
      values[col.columnKey] = col.columnType === "DATETIME" ? localToIso(raw) : raw;
    }
    for (const col of columns) {
      if (!col.required) continue;
      const has = col.columnType === "BOOL" ? true : (values[col.columnKey] ?? "") !== "";
      if (!has) {
        setError(`'${col.columnLabel}'은(는) 필수입니다.`);
        return;
      }
    }
    setSaving(true);
    setError(null);
    try {
      await createLog.mutateAsync({
        loggedAt: entry.loggedAtLocal ? localToIso(entry.loggedAtLocal) : null,
        values,
        notes: entry.notes.trim() || null,
      });
      setEntry(null);
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 400
          ? "값이 올바르지 않습니다 (타입·필수 항목 확인)."
          : "기록에 실패했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  async function submitColumn(e: React.FormEvent) {
    e.preventDefault();
    if (!colForm || !selected || !colForm.columnLabel.trim()) return;
    const enumValues =
      colForm.columnType === "ENUM"
        ? colForm.enumText
            .split(",")
            .map((s) => s.trim())
            .filter(Boolean)
        : null;
    if (colForm.columnType === "ENUM" && (!enumValues || enumValues.length === 0)) {
      setError("선택 항목을 쉼표로 입력하세요 (예: 양호, 점검필요).");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await createColumn.mutateAsync({
        columnKey: `c${Math.random().toString(36).slice(2, 10)}`,
        columnLabel: colForm.columnLabel.trim(),
        columnType: colForm.columnType,
        enumValues,
        required: colForm.required,
        defaultValue: colForm.defaultValue.trim() || null,
      });
      setColForm(null);
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 409
          ? "같은 컬럼이 이미 있습니다."
          : "컬럼 추가에 실패했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  async function onDeleteColumn(col: LogColumn) {
    const ok = await dialogs.confirm({
      title: "컬럼 삭제",
      message: `'${col.columnLabel}' 컬럼을 삭제할까요? (기존 기록의 값은 남습니다)`,
      confirmLabel: "삭제",
      danger: true,
    });
    if (ok) deleteColumn.mutate(col.id);
  }

  if (equipment.length === 0) {
    return (
      <div className="eq-empty">
        <p>로그를 남길 활성 장비가 없습니다. 먼저 장비를 등록하세요.</p>
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
        <div className="eq-resv-bar-acts">
          {isAdmin && (
            <button
              type="button"
              className="eq-close"
              onClick={() => setManageCols((v) => !v)}
              title="로그 컬럼 정의 관리"
            >
              ⚙ 컬럼 관리
            </button>
          )}
          <button type="button" className="eq-add" onClick={openEntry}>
            ＋ 기록
          </button>
        </div>
      </div>

      {manageCols && (
        <div className="eq-cols">
          <div className="eq-cols-head">
            로그 컬럼{" "}
            <button
              type="button"
              className="eq-act"
              onClick={() =>
                setColForm({
                  columnLabel: "",
                  columnType: "TEXT",
                  required: false,
                  enumText: "",
                  defaultValue: "",
                })
              }
            >
              ＋ 컬럼 추가
            </button>
          </div>
          {columns.length === 0 ? (
            <div className="eq-cols-empty">정의된 컬럼이 없습니다. 메모만 기록할 수 있습니다.</div>
          ) : (
            <ul className="eq-cols-list">
              {columns.map((c) => (
                <li key={c.id} className="eq-col-row">
                  <span className="eq-col-label">{c.columnLabel}</span>
                  <span className="eq-col-type">{TYPE_LABEL[c.columnType]}</span>
                  {c.required && <span className="eq-resv-tag">필수</span>}
                  <button type="button" className="eq-act danger" onClick={() => onDeleteColumn(c)}>
                    삭제
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {logsQ.isLoading ? (
        <div className="eq-msg">불러오는 중...</div>
      ) : logs.length === 0 ? (
        <div className="eq-empty">
          <p>기록이 없습니다.</p>
          <button type="button" className="eq-add" onClick={openEntry}>
            ＋ 첫 기록
          </button>
        </div>
      ) : (
        <div className="eq-log-table-wrap">
          <table className="eq-log-table">
            <thead>
              <tr>
                <th>일시</th>
                {columns.map((c) => (
                  <th key={c.id}>{c.columnLabel}</th>
                ))}
                <th>메모</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((row) => (
                <tr key={row.id}>
                  <td className="eq-log-time">{fmtDateTime(row.loggedAt)}</td>
                  {columns.map((c) => (
                    <td key={c.id}>{renderCell(c, row.values[c.columnKey])}</td>
                  ))}
                  <td className="eq-log-notes">{row.notes || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* 컬럼 추가 폼 */}
      {colForm && (
        <div className="eq-overlay" onClick={() => setColForm(null)}>
          <form className="eq-form" onClick={(e) => e.stopPropagation()} onSubmit={submitColumn}>
            <header className="eq-form-head">새 로그 컬럼</header>
            <label className="eq-field">
              <span>
                이름 <em className="req">*</em>
              </span>
              <input
                className="eq-input"
                value={colForm.columnLabel}
                onChange={(e) => setColForm({ ...colForm, columnLabel: e.target.value })}
                placeholder="예: 사용 시간, 상태"
                autoFocus
                maxLength={200}
              />
            </label>
            <label className="eq-field">
              <span>유형</span>
              <select
                className="eq-input"
                value={colForm.columnType}
                onChange={(e) =>
                  setColForm({ ...colForm, columnType: e.target.value as LogColumnType })
                }
              >
                {(Object.keys(TYPE_LABEL) as LogColumnType[]).map((t) => (
                  <option key={t} value={t}>
                    {TYPE_LABEL[t]}
                  </option>
                ))}
              </select>
            </label>
            {colForm.columnType === "ENUM" && (
              <label className="eq-field">
                <span>선택 항목 (쉼표 구분)</span>
                <input
                  className="eq-input"
                  value={colForm.enumText}
                  onChange={(e) => setColForm({ ...colForm, enumText: e.target.value })}
                  placeholder="양호, 점검필요, 고장"
                />
              </label>
            )}
            <label className="eq-check-row">
              <input
                type="checkbox"
                checked={colForm.required}
                onChange={(e) => setColForm({ ...colForm, required: e.target.checked })}
              />
              필수 입력
            </label>
            {error && <div className="eq-form-err">{error}</div>}
            <footer className="eq-form-foot">
              <button type="button" className="eq-cancel" onClick={() => setColForm(null)}>
                취소
              </button>
              <button
                type="submit"
                className="eq-submit"
                disabled={!colForm.columnLabel.trim() || saving}
              >
                추가
              </button>
            </footer>
          </form>
        </div>
      )}

      {/* 기록 입력 폼 */}
      {entry && (
        <div className="eq-overlay" onClick={() => setEntry(null)}>
          <form className="eq-form" onClick={(e) => e.stopPropagation()} onSubmit={submitEntry}>
            <header className="eq-form-head">새 기록</header>
            <label className="eq-field">
              <span>일시</span>
              <input
                type="datetime-local"
                className="eq-input"
                value={entry.loggedAtLocal}
                onChange={(e) => setEntry({ ...entry, loggedAtLocal: e.target.value })}
              />
            </label>
            {columns.map((col) => {
              const val = entry.values[col.columnKey] ?? "";
              return (
                <label key={col.id} className="eq-field">
                  <span>
                    {col.columnLabel}
                    {col.required && <em className="req"> *</em>}
                  </span>
                  {col.columnType === "BOOL" ? (
                    <span className="eq-check-row">
                      <input
                        type="checkbox"
                        checked={val === "true"}
                        onChange={(e) => setVal(col.columnKey, e.target.checked ? "true" : "false")}
                      />
                      예
                    </span>
                  ) : col.columnType === "ENUM" ? (
                    <select
                      className="eq-input"
                      value={val}
                      onChange={(e) => setVal(col.columnKey, e.target.value)}
                    >
                      <option value="">선택</option>
                      {(col.enumValues ?? []).map((v) => (
                        <option key={v} value={v}>
                          {v}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <input
                      className="eq-input"
                      type={
                        col.columnType === "NUMBER"
                          ? "number"
                          : col.columnType === "DATETIME"
                            ? "datetime-local"
                            : "text"
                      }
                      value={val}
                      onChange={(e) => setVal(col.columnKey, e.target.value)}
                    />
                  )}
                </label>
              );
            })}
            <label className="eq-field">
              <span>메모</span>
              <textarea
                className="eq-input eq-textarea"
                value={entry.notes}
                onChange={(e) => setEntry({ ...entry, notes: e.target.value })}
                rows={2}
                maxLength={2000}
              />
            </label>
            {error && <div className="eq-form-err">{error}</div>}
            <footer className="eq-form-foot">
              <button type="button" className="eq-cancel" onClick={() => setEntry(null)}>
                취소
              </button>
              <button type="submit" className="eq-submit" disabled={saving}>
                {saving ? "기록 중..." : "기록"}
              </button>
            </footer>
          </form>
        </div>
      )}
    </div>
  );
}
