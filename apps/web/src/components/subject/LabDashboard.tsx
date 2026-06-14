import { useMemo, useState } from "react";
import type { Card } from "../../lib/graphTypes";
import { useLabMembers, useLabPendingCards, useMyLabRole } from "../../lib/labs";
import { EQUIPMENT_DOC_ID } from "../../lib/route";
import { useAnnouncements, useSharedCalendars } from "../../lib/sharedCalendars";
import { useSubjects } from "../../lib/subjects";
import { pushRoute, useRoute } from "../../lib/useRoute";
import { LabPanel } from "./LabPanel";
import "./LabDashboard.css";

const MS_PER_DAY = 86_400_000;
const RANGE_DAYS_BACK = 30; // 공지 조회 하한(최근 지난 공지도 포함)
const RANGE_DAYS_FWD = 90; // 공지 조회 상한(예정 공지)
const MAX_ANNOUNCEMENTS = 5; // 대시보드 요약에 노출할 최근 공지 수

/**
 * 연구실 홈(대시보드) — 연구실 진입 시 메인 doctab으로 열리는 랜딩.
 *
 * 공지·인원정보를 기존 훅으로 요약하고(작성·관리는 장비 화면·LabPanel), 현행 과제는 자리만 둔다(데이터 후속).
 * 작업공간(탭·DAG)·장비로의 이동은 pushRoute 직접 호출(콜백 불요) — 라우팅 정본이 URL이라 자족적이다.
 * 개인 주제에는 이 화면이 없다(LAB 전용 — SubjectWorkspace가 kind 가드).
 */
export function LabDashboard({
  subjectId,
  subjectName,
}: {
  subjectId: string;
  subjectName: string;
}) {
  const route = useRoute();
  const subjects = useSubjects();
  const subject = subjects.data?.find((s) => s.id === subjectId) ?? null;
  const { role, isAdmin } = useMyLabRole(subjectId);
  const members = useLabMembers(subjectId, true);
  const pending = useLabPendingCards(subjectId, true);
  const calendars = useSharedCalendars();
  const firstCalendarId = calendars.data?.[0]?.id ?? null;

  // 조회 범위는 마운트 시 1회 고정(쿼리 키 안정화) — EquipmentReservations 선례.
  const [range] = useState(() => {
    const now = Date.now();
    return {
      fromIso: new Date(now - RANGE_DAYS_BACK * MS_PER_DAY).toISOString(),
      toIso: new Date(now + RANGE_DAYS_FWD * MS_PER_DAY).toISOString(),
    };
  });
  const announcements = useAnnouncements(firstCalendarId, range.fromIso, range.toIso);
  const recent = useMemo(
    () =>
      [...(announcements.data ?? [])]
        .sort((a, b) => b.startAt.localeCompare(a.startAt))
        .slice(0, MAX_ANNOUNCEMENTS),
    [announcements.data],
  );

  const [panelOpen, setPanelOpen] = useState(false);

  function openBoard() {
    pushRoute({ subjectId, tabId: route.tabId, view: "canvas", doc: null });
  }
  function openEquipment() {
    pushRoute({
      subjectId,
      tabId: route.tabId,
      view: route.view,
      doc: { kind: "equipment", id: EQUIPMENT_DOC_ID },
    });
  }
  function openCard(c: Card) {
    if (!c.tabId) return; // 탭 미배치 카드는 이동 대상 없음
    pushRoute({ subjectId, tabId: c.tabId, view: "canvas", doc: { kind: "card", id: c.id } });
  }

  return (
    <div className="lab-dash">
      <header className="lab-dash-head">
        <div className="lab-dash-title">
          <span className="lab-dash-kicker">연구실</span>
          <h1 className="lab-dash-h1">{subjectName}</h1>
        </div>
        <div className="lab-dash-acts">
          {role && <span className="lab-dash-role">{role}</span>}
          <button type="button" className="lab-dash-btn" onClick={openBoard}>
            ▭ 작업공간
          </button>
          <button type="button" className="lab-dash-btn" onClick={openEquipment}>
            ⚗ 장비
          </button>
          {isAdmin && subject && (
            <button type="button" className="lab-dash-btn" onClick={() => setPanelOpen(true)}>
              ⚙ 관리
            </button>
          )}
        </div>
      </header>

      <div className="lab-dash-grid">
        <section className="lab-dash-card wide">
          <h2 className="lab-dash-card-h">공지</h2>
          {!firstCalendarId ? (
            <p className="lab-dash-empty">공지 캘린더가 없습니다. 장비 → 공지에서 만들 수 있습니다.</p>
          ) : recent.length === 0 ? (
            <p className="lab-dash-empty">최근 공지가 없습니다.</p>
          ) : (
            <ul className="lab-dash-anc">
              {recent.map((a) => (
                <li key={a.id} className="lab-dash-anc-row">
                  <span className="lab-dash-anc-when">{fmtShortDate(a.startAt)}</span>
                  <span className="lab-dash-anc-title">{a.title}</span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="lab-dash-card">
          <h2 className="lab-dash-card-h">인원 · {members.data?.length ?? 0}명</h2>
          <ul className="lab-dash-members">
            {(members.data ?? []).map((m) => (
              <li key={m.userId} className="lab-dash-member-row">
                <span className="lab-dash-member-name">
                  {m.displayName ?? m.email ?? m.userId.slice(0, 8)}
                </span>
                <span className="lab-dash-member-role">{m.role}</span>
              </li>
            ))}
            {members.isLoading && <li className="lab-dash-empty">불러오는 중...</li>}
            {!members.isLoading && (members.data ?? []).length === 0 && (
              <li className="lab-dash-empty">멤버 없음</li>
            )}
          </ul>
        </section>

        <section className="lab-dash-card">
          <h2 className="lab-dash-card-h">현행 과제</h2>
          {pending.isLoading ? (
            <p className="lab-dash-empty">불러오는 중...</p>
          ) : (pending.data ?? []).length === 0 ? (
            <p className="lab-dash-empty">진행 중인 과제가 없습니다.</p>
          ) : (
            <ul className="lab-dash-tasks">
              {(pending.data ?? []).map((c) => (
                <li key={c.id} className="lab-dash-task-row">
                  <button type="button" className="lab-dash-task" onClick={() => openCard(c)}>
                    <span className="lab-dash-task-when">
                      {c.startDate ? fmtCardDate(c.startDate) : "—"}
                    </span>
                    <span className="lab-dash-task-title">{c.title}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      {panelOpen && subject && <LabPanel subject={subject} onClose={() => setPanelOpen(false)} />}
    </div>
  );
}

/** ISO datetime → "M/D" 짧은 날짜(대시보드 공지 요약용). */
function fmtShortDate(iso: string): string {
  const d = new Date(iso);
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

/** 카드 startDate("YYYY-MM-DD" date)는 split 파싱 — new Date는 UTC 자정으로 해석돼 로컬에서 하루 어긋날 수 있다. */
function fmtCardDate(isoDate: string): string {
  const [, m, d] = isoDate.split("-");
  return `${Number(m)}/${Number(d)}`;
}
