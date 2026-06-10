import { lazy, Suspense, useMemo, useState } from "react";
import { ApiError } from "../../lib/api";
import {
  useAnnouncements,
  useCreateSharedCalendar,
  useDeleteAnnouncement,
  useDeleteSharedCalendar,
  usePostAnnouncement,
  useSharedCalendars,
  type Announcement,
} from "../../lib/equipment";
import { useDialogs } from "../ui/DialogProvider";
import { localToIso, toLocalInput } from "./EquipmentReservations";

// 본문 마크다운 렌더는 카드 본문과 청크 공유(KaTeX 포함) — 공지에 본문이 있을 때만 로드.
const MarkdownView = lazy(() =>
  import("./MarkdownDoc").then((m) => ({ default: m.MarkdownView })),
);

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];
const DAY_MS = 86400000;

function fmtWhen(startIso: string, endIso: string | null): string {
  const s = new Date(startIso);
  const d = (x: Date) => `${x.getMonth() + 1}/${x.getDate()}(${WEEKDAYS[x.getDay()]})`;
  const t = (x: Date) =>
    `${String(x.getHours()).padStart(2, "0")}:${String(x.getMinutes()).padStart(2, "0")}`;
  const start = `${d(s)} ${t(s)}`;
  if (!endIso) return start;
  const e = new Date(endIso);
  const sameDay = s.toDateString() === e.toDateString();
  return sameDay ? `${start} – ${t(e)}` : `${start} – ${d(e)} ${t(e)}`;
}

interface PostForm {
  startLocal: string;
  endLocal: string;
  title: string;
  body: string;
}

/**
 * 공용 캘린더 공지 — 주제 공유 공지 게시판. 캘린더(여러 개 가능) 선택/생성 + 날짜 공지 작성/삭제.
 * 공지 본문은 마크다운 렌더(카드 본문과 동일한 MarkdownView, lazy). 기본 4줄 클램프, 클릭으로 펼침.
 * 시각은 브라우저 로컬↔ISO.
 */
export function SharedCalendarView() {
  const dialogs = useDialogs();
  const calendarsQ = useSharedCalendars();
  const createCal = useCreateSharedCalendar();
  const deleteCal = useDeleteSharedCalendar();
  const deleteAnc = useDeleteAnnouncement();

  const calendars = calendarsQ.data ?? [];
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = selectedId ?? calendars[0]?.id ?? null;
  const selectedCal = calendars.find((c) => c.id === selected) ?? null;

  const postAnc = usePostAnnouncement(selected ?? "");

  // 조회 범위 — 마운트 시 1회 고정(now-30일 ~ now+365일).
  // useState lazy 초기화 = 최초 렌더 1회 실행 — "1회 고정" 의도와 렌더 순수성 규칙이 일치.
  const [range] = useState(() => {
    const now = Date.now();
    return {
      from: new Date(now - 30 * DAY_MS).toISOString(),
      to: new Date(now + 365 * DAY_MS).toISOString(),
    };
  });
  const ancQ = useAnnouncements(selected, range.from, range.to);

  const [form, setForm] = useState<PostForm | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  // 본문 펼침 상태(공지 id 집합) — 기본은 4줄 클램프.
  const [expandedIds, setExpandedIds] = useState<ReadonlySet<string>>(new Set());

  function toggleBody(id: string, e: React.MouseEvent) {
    // 본문 안 링크 클릭은 펼침 토글로 먹지 않는다.
    if ((e.target as HTMLElement).closest("a")) return;
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  const sorted = useMemo(
    () => [...(ancQ.data ?? [])].sort((a, b) => a.startAt.localeCompare(b.startAt)),
    [ancQ.data],
  );
  // past 분류 기준 시각 — 렌더 순수성을 위해 Date.now() 대신 마지막 fetch 시각(refetch 시 갱신).
  const now = ancQ.dataUpdatedAt;

  async function onCreateCalendar() {
    const name = await dialogs.prompt({
      title: "새 공지 캘린더",
      placeholder: "캘린더 이름 (예: 연구실 공지)",
    });
    if (!name || !name.trim()) return;
    try {
      const cal = await createCal.mutateAsync({ name: name.trim(), announcementOnly: true });
      setSelectedId(cal.id);
    } catch {
      await dialogs.alert({ title: "생성 실패", message: "캘린더를 만들지 못했습니다." });
    }
  }

  async function onDeleteCalendar() {
    if (!selectedCal) return;
    const ok = await dialogs.confirm({
      title: "캘린더 삭제",
      message: `"${selectedCal.name}" 캘린더와 그 공지를 삭제할까요?`,
      confirmLabel: "삭제",
      danger: true,
    });
    if (!ok) return;
    await deleteCal.mutateAsync(selectedCal.id);
    setSelectedId(null);
  }

  function openPost() {
    const start = new Date();
    start.setMinutes(0, 0, 0);
    start.setHours(start.getHours() + 1);
    setFormError(null);
    setForm({ startLocal: toLocalInput(start), endLocal: "", title: "", body: "" });
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!form || !selected || !form.title.trim() || !form.startLocal) {
      setFormError("제목과 시작 일시는 필수입니다.");
      return;
    }
    if (form.endLocal && new Date(form.endLocal).getTime() < new Date(form.startLocal).getTime()) {
      setFormError("종료는 시작보다 빠를 수 없습니다.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      await postAnc.mutateAsync({
        startAt: localToIso(form.startLocal),
        endAt: form.endLocal ? localToIso(form.endLocal) : null,
        title: form.title.trim(),
        bodyMd: form.body.trim() || null,
      });
      setForm(null);
    } catch (err) {
      setFormError(
        err instanceof ApiError && err.status === 400
          ? "입력이 올바르지 않습니다."
          : "공지 작성에 실패했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  async function onDeleteAnc(a: Announcement) {
    const ok = await dialogs.confirm({
      title: "공지 삭제",
      message: `"${a.title}" 공지를 삭제할까요?`,
      confirmLabel: "삭제",
      danger: true,
    });
    if (!ok) return;
    try {
      await deleteAnc.mutateAsync(a.id);
    } catch {
      await dialogs.alert({ title: "삭제 불가", message: "공지를 삭제하지 못했습니다." });
    }
  }

  if (calendarsQ.isLoading) return <div className="eq-msg">불러오는 중...</div>;

  if (calendars.length === 0) {
    return (
      <div className="eq-empty">
        <p>공지 캘린더가 없습니다.</p>
        <button type="button" className="eq-add" onClick={onCreateCalendar}>
          ＋ 공지 캘린더 만들기
        </button>
      </div>
    );
  }

  return (
    <div className="eq-resv-view">
      <div className="eq-resv-bar">
        <label className="eq-resv-pick">
          캘린더
          <select
            className="eq-input"
            value={selected ?? ""}
            onChange={(e) => setSelectedId(e.target.value)}
          >
            {calendars.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <div className="eq-resv-bar-acts">
          <button type="button" className="eq-close" onClick={onCreateCalendar}>
            ＋ 캘린더
          </button>
          {selectedCal && (
            <button
              type="button"
              className="eq-close"
              onClick={onDeleteCalendar}
              title="캘린더 삭제"
            >
              ✕ 캘린더
            </button>
          )}
          <button type="button" className="eq-add" onClick={openPost}>
            ＋ 공지
          </button>
        </div>
      </div>

      {ancQ.isLoading ? (
        <div className="eq-msg">불러오는 중...</div>
      ) : sorted.length === 0 ? (
        <div className="eq-empty">
          <p>공지가 없습니다.</p>
          <button type="button" className="eq-add" onClick={openPost}>
            ＋ 첫 공지
          </button>
        </div>
      ) : (
        <ul className="eq-anc-list">
          {sorted.map((a) => {
            const past = new Date(a.endAt ?? a.startAt).getTime() < now;
            return (
              <li key={a.id} className={`eq-anc${past ? " past" : ""}`}>
                <div className="eq-anc-when">{fmtWhen(a.startAt, a.endAt)}</div>
                <div className="eq-anc-main">
                  <div className="eq-anc-title">{a.title}</div>
                  {a.bodyMd && (
                    <div
                      className={`eq-anc-body${expandedIds.has(a.id) ? " open" : ""}`}
                      onClick={(e) => toggleBody(a.id, e)}
                      title={expandedIds.has(a.id) ? "클릭하여 접기" : "클릭하여 펼치기"}
                    >
                      <Suspense fallback={<>{a.bodyMd}</>}>
                        <MarkdownView source={a.bodyMd} />
                      </Suspense>
                    </div>
                  )}
                </div>
                <button type="button" className="eq-act danger" onClick={() => onDeleteAnc(a)}>
                  삭제
                </button>
              </li>
            );
          })}
        </ul>
      )}

      {form && (
        <div className="eq-overlay" onClick={() => setForm(null)}>
          <form className="eq-form" onClick={(e) => e.stopPropagation()} onSubmit={onSubmit}>
            <header className="eq-form-head">새 공지 · {selectedCal?.name}</header>
            <label className="eq-field">
              <span>
                제목 <em className="req">*</em>
              </span>
              <input
                className="eq-input"
                value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value })}
                placeholder="예: 정기 점검 안내"
                autoFocus
                maxLength={300}
              />
            </label>
            <label className="eq-field">
              <span>
                시작 <em className="req">*</em>
              </span>
              <input
                type="datetime-local"
                className="eq-input"
                value={form.startLocal}
                onChange={(e) => setForm({ ...form, startLocal: e.target.value })}
              />
            </label>
            <label className="eq-field">
              <span>종료 (선택)</span>
              <input
                type="datetime-local"
                className="eq-input"
                value={form.endLocal}
                onChange={(e) => setForm({ ...form, endLocal: e.target.value })}
              />
            </label>
            <label className="eq-field">
              <span>내용</span>
              <textarea
                className="eq-input eq-textarea"
                value={form.body}
                onChange={(e) => setForm({ ...form, body: e.target.value })}
                placeholder="공지 내용 — 마크다운 지원 (제목 #, 목록 -, 링크, 표, 수식 $..$)"
                rows={5}
                maxLength={2000}
              />
            </label>
            {formError && <div className="eq-form-err">{formError}</div>}
            <footer className="eq-form-foot">
              <button type="button" className="eq-cancel" onClick={() => setForm(null)}>
                취소
              </button>
              <button
                type="submit"
                className="eq-submit"
                disabled={!form.title.trim() || !form.startLocal || saving}
              >
                {saving ? "작성 중..." : "공지"}
              </button>
            </footer>
          </form>
        </div>
      )}
    </div>
  );
}
