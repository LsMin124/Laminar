import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ApiError, getCurrentSubjectId, setCurrentSubjectId } from "../../lib/api";
import { useMe } from "../../lib/auth";
import { dagKeys } from "../../lib/dagKeys";
import type { Subject } from "../../lib/graphTypes";
import { useJoinLab, usePromoteToLab } from "../../lib/labs";
import { useCreateSubject, useDeleteSubject, useSubjects, useUpdateSubject } from "../../lib/subjects";
import { pushRoute, replaceRoute, useRoute } from "../../lib/useRoute";
import { useDialogs } from "../ui/DialogProvider";
import { LabPanel } from "./LabPanel";
import { SubjectWorkspace } from "./SubjectWorkspace";
import { Identicon } from "./Identicon";
import "./SubjectLayout.css";

/**
 * 좌측 얇은 아이콘 레일(주제 전환) + 활성 주제의 SubjectWorkspace + 주제 관리 모달.
 * 레일=빠른 전환만, 세부사항(목록·이름변경·생성·삭제)은 별도 모달 창에서.
 * 주제 전환 시 X-Laminar-Subject-Id 헤더 변경 + tabs/graph 캐시 제거 + key 리마운트.
 * 레일 하단(rail-future)=전역 도구: 장비 관리(플라스크, doctab 오픈) + 학습 정리(준비 중 ghost).
 *
 * DX-3: 활성 주제의 정본은 URL(/s/{subjectId}) — localStorage는 API 헤더용 follower.
 * URL이 없거나(루트 진입) 무효(남의/삭제된 주제)면 저장값→첫 주제 순으로 보정(replace).
 */
export function SubjectLayout() {
  const subjects = useSubjects();
  const me = useMe();
  const createSubject = useCreateSubject();
  const updateSubject = useUpdateSubject();
  const deleteSubject = useDeleteSubject();
  const promoteToLab = usePromoteToLab();
  const joinLab = useJoinLab();
  const dialogs = useDialogs();
  const qc = useQueryClient();
  const route = useRoute();
  const [activeId, setActiveId] = useState<string | null>(null);
  const [manageOpen, setManageOpen] = useState(false);
  const [labPanelFor, setLabPanelFor] = useState<Subject | null>(null);
  // '주제 본문' 신호 — 증가시키면 활성 주제의 SubjectWorkspace가 본문 문서를 연다(레일 ▤ 버튼).
  const [bodyNonce, setBodyNonce] = useState(0);
  // '장비 관리' 신호 — 증가시키면 SubjectWorkspace가 장비 doctab 창을 연다(레일 플라스크 버튼).
  const [equipmentNonce, setEquipmentNonce] = useState(0);
  const [hoverTip, setHoverTip] = useState<{ name: string; y: number } | null>(null);

  const list = subjects.data ?? [];

  function purgeSubjectCaches() {
    // tabs/tabGraph는 주제-무관 키(헤더 기반 API)라 주제가 바뀌면 비워야 한다.
    qc.removeQueries({ queryKey: dagKeys.tabs });
    qc.removeQueries({ queryKey: dagKeys.tabGraphs });
  }

  // URL(정본) → 활성 주제 보정·동기화. 헤더(setCurrentSubjectId)를 마운트 트리거(setActiveId)보다
  // 먼저 동기 호출해야 자식(useTabs)의 첫 fetch가 새 주제 헤더로 나간다(자식 effect가 부모보다 선행).
  // 뒤로가기/딥링크로 주제가 바뀌는 경로에도 캐시 purge가 동작하도록 직전 값을 추적한다.
  const prevActiveRef = useRef<string | null>(null);
  useEffect(() => {
    if (!subjects.data) return;
    const urlValid = !!route.subjectId && subjects.data.some((s) => s.id === route.subjectId);
    const stored = getCurrentSubjectId();
    const storedValid = !!stored && subjects.data.some((s) => s.id === stored);
    const next = urlValid
      ? route.subjectId
      : storedValid
        ? stored
        : (subjects.data[0]?.id ?? null);
    setCurrentSubjectId(next);
    if (prevActiveRef.current !== null && next !== prevActiveRef.current) purgeSubjectCaches();
    prevActiveRef.current = next;
    setActiveId(next);
    if (next && !urlValid) {
      replaceRoute({ subjectId: next, tabId: null, view: "canvas", doc: null });
    } else if (!next && route.subjectId) {
      replaceRoute({ subjectId: null, tabId: null, view: "canvas", doc: null });
    }
  }, [subjects.data, route.subjectId]);

  function switchSubject(id: string) {
    if (id === activeId) return;
    setCurrentSubjectId(id); // 헤더 먼저(아래 마운트 트리거보다 선행)
    prevActiveRef.current = id;
    purgeSubjectCaches();
    setActiveId(id);
    pushRoute({ subjectId: id, tabId: null, view: "canvas", doc: null });
  }

  async function onCreateSubject() {
    const name = await dialogs.prompt({ title: "새 연구 주제", placeholder: "주제 이름" });
    if (!name || !name.trim()) return;
    const created = await createSubject.mutateAsync(name.trim());
    switchSubject(created.id);
  }

  async function onRename(s: Subject) {
    // PATCH /current는 활성 주제를 대상으로 하므로, 비활성이면 먼저 전환한다.
    if (s.id !== activeId) switchSubject(s.id);
    const name = await dialogs.prompt({ title: "주제 이름 변경", defaultValue: s.name });
    if (!name || !name.trim() || name.trim() === s.name) return;
    await updateSubject.mutateAsync({ id: s.id, name: name.trim() });
  }

  async function onDelete(s: Subject) {
    // DELETE /current는 활성 주제를 대상으로 하므로, 비활성이면 먼저 전환한다.
    if (s.id !== activeId) switchSubject(s.id);
    const ok = await dialogs.confirm({
      title: "주제 삭제",
      message: `"${s.name}"와(과) 그 안의 모든 탭·카드·관계가 영구 삭제됩니다. 되돌릴 수 없습니다. 계속할까요?`,
      confirmLabel: "삭제",
      danger: true,
    });
    if (!ok) return;
    try {
      await deleteSubject.mutateAsync();
    } catch {
      await dialogs.alert({
        title: "삭제 실패",
        message: "주제를 삭제하지 못했습니다. 소유자만 삭제할 수 있습니다.",
      });
      return;
    }
    // 활성 주제가 사라졌으니 헤더/활성 초기화 → 목록 재조회 시 보정 effect가 첫 주제를 재선정(replace).
    setCurrentSubjectId(null);
    setActiveId(null);
    purgeSubjectCaches();
    await qc.invalidateQueries({ queryKey: dagKeys.subjects });
  }

  /** LAB 승격 — OWNER 전용·비가역. /current 대상이라 비활성이면 먼저 전환(onRename 선례). */
  async function onPromote(s: Subject) {
    const ok = await dialogs.confirm({
      title: "LAB으로 승격",
      message: `"${s.name}"을(를) LAB으로 승격합니다. 연구원을 초대해 장비·공지를 공유할 수 있게 되며, 되돌릴 수 없습니다. 계속할까요?`,
      confirmLabel: "승격",
    });
    if (!ok) return;
    if (s.id !== activeId) switchSubject(s.id);
    await promoteToLab.mutateAsync();
  }

  /** LAB 관리 패널 — /current 기반 API라 해당 lab으로 먼저 전환. */
  function onOpenLabPanel(s: Subject) {
    if (s.id !== activeId) switchSubject(s.id);
    setLabPanelFor(s);
  }

  /** 초대코드로 LAB 가입 신청 — 승인 후 멤버가 되면 주제 목록에 나타난다. */
  async function onJoinByCode() {
    const code = await dialogs.prompt({ title: "LAB 가입", placeholder: "초대코드 입력 (예: ABCD2345)" });
    if (!code || !code.trim()) return;
    try {
      const outcome = await joinLab.mutateAsync(code.trim());
      await dialogs.alert({
        title: "가입 신청 완료",
        message: `"${outcome.labName}"에 가입을 신청했습니다. 관리자 승인 후 주제 목록에 나타납니다.`,
      });
    } catch (e) {
      // lab 가입 오류는 서버가 한국어 사용자 문구를 envelope.message로 보낸다(코드 매핑 대상 아님).
      const body = e instanceof ApiError ? (e.body as { message?: string } | null) : null;
      await dialogs.alert({
        title: "가입 신청 실패",
        message: body?.message ?? "초대코드를 확인하세요.",
      });
    }
  }

  const activeValid = !!activeId && list.some((s) => s.id === activeId);
  const activeSubject = list.find((s) => s.id === activeId) ?? null;

  return (
    <div className="lay">
      <aside className="rail">
        <div className="rail-brand" title="LAMINAR">L</div>

        <div className="rail-subjects">
          {list.map((s) => (
            <button
              key={s.id}
              type="button"
              className={`rail-tile${s.id === activeId ? " active" : ""}`}
              onClick={() => switchSubject(s.id)}
              onMouseEnter={(e) => {
                const r = e.currentTarget.getBoundingClientRect();
                setHoverTip({ name: s.name, y: r.top + r.height / 2 });
              }}
              onMouseLeave={() => setHoverTip(null)}
              aria-label={s.name}
            >
              <Identicon seed={s.id} size={28} />
            </button>
          ))}
          {activeValid && (
            <button
              type="button"
              className="rail-btn body"
              onClick={() => setBodyNonce((n) => n + 1)}
              title="현재 주제 본문 열기"
              aria-label="주제 본문"
            >
              ▤
            </button>
          )}
          <button type="button" className="rail-btn" onClick={onCreateSubject} title="새 주제">
            ＋
          </button>
          <button type="button" className="rail-btn" onClick={() => setManageOpen(true)} title="주제 관리">
            ⋯
          </button>
        </div>

        <div className="rail-future">
          {/* 장비 관리(공용 자원) — LAB 전용 표면(L3 재스코프): 활성 주제가 lab일 때만 노출. */}
          {activeValid && activeSubject?.kind === "LAB" && (
            <button
              type="button"
              className="rail-tile tool"
              onClick={() => setEquipmentNonce((n) => n + 1)}
              onMouseEnter={(e) => {
                const r = e.currentTarget.getBoundingClientRect();
                setHoverTip({ name: "장비 관리", y: r.top + r.height / 2 });
              }}
              onMouseLeave={() => setHoverTip(null)}
              aria-label="장비 관리"
            >
              <svg
                viewBox="0 0 24 24"
                width="20"
                height="20"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinejoin="miter"
                strokeLinecap="square"
                aria-hidden="true"
              >
                <path d="M9.5 3.5 H14.5" />
                <path d="M10.5 3.5 V9 L4.8 19 H19.2 L13.5 9 V3.5" />
                <path d="M7.4 14.5 H16.6" />
              </svg>
            </button>
          )}
          <button type="button" className="rail-tile ghost" disabled title="학습 정리 (준비 중)">
            학
          </button>
        </div>
      </aside>

      <main className="lay-main">
        {activeValid ? (
          <SubjectWorkspace
            key={activeId}
            subjectId={activeId ?? ""}
            subjectName={list.find((s) => s.id === activeId)?.name ?? ""}
            openSubjectBodyNonce={bodyNonce}
            openEquipmentNonce={equipmentNonce}
          />
        ) : (
          <div className="lay-empty">
            {subjects.isLoading ? "불러오는 중..." : "연구 주제를 만들어 시작하세요 (좌측 ＋)."}
          </div>
        )}
      </main>

      {hoverTip && (
        <div className="rail-tip" style={{ top: hoverTip.y }}>
          {hoverTip.name}
        </div>
      )}

      {manageOpen && (
        <div className="subj-overlay" onClick={() => setManageOpen(false)}>
          <div
            className="subj-modal"
            role="dialog"
            aria-label="주제 관리"
            onClick={(e) => e.stopPropagation()}
          >
            <header className="subj-head">
              <strong>주제 관리</strong>
              <button type="button" className="subj-x" onClick={() => setManageOpen(false)} aria-label="닫기">
                ✕
              </button>
            </header>

            <ul className="subj-list">
              {list.map((s) => (
                <li key={s.id} className={`subj-row${s.id === activeId ? " active" : ""}`}>
                  <button
                    type="button"
                    className="subj-name"
                    onClick={() => switchSubject(s.id)}
                    title="이 주제로 전환"
                  >
                    <Identicon seed={s.id} size={18} />
                    {s.name}
                    {s.kind === "LAB" && <span className="lab-badge">LAB</span>}
                  </button>
                  {s.kind === "LAB" ? (
                    <button type="button" className="subj-act" onClick={() => onOpenLabPanel(s)}>
                      LAB 관리
                    </button>
                  ) : (
                    s.ownerUserId === me.data?.userId && (
                      <button type="button" className="subj-act" onClick={() => void onPromote(s)}>
                        LAB 승격
                      </button>
                    )
                  )}
                  <button type="button" className="subj-act" onClick={() => onRename(s)}>
                    이름변경
                  </button>
                  <button type="button" className="subj-act danger" onClick={() => onDelete(s)}>
                    삭제
                  </button>
                </li>
              ))}
              {list.length === 0 && <li className="subj-empty">주제 없음</li>}
            </ul>

            <footer className="subj-foot">
              <button type="button" className="subj-create" onClick={onCreateSubject}>
                ＋ 새 주제
              </button>
              <button type="button" className="subj-create" onClick={() => void onJoinByCode()}>
                ⌗ 코드로 LAB 가입
              </button>
            </footer>
          </div>
        </div>
      )}

      {labPanelFor && (
        <LabPanel subject={labPanelFor} onClose={() => setLabPanelFor(null)} />
      )}
    </div>
  );
}
