import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ApiError, getCurrentSubjectId, setCurrentSubjectId } from "../../lib/api";
import { useMe } from "../../lib/auth";
import { dagKeys } from "../../lib/dagKeys";
import type { Subject } from "../../lib/graphTypes";
import { useJoinLab, usePromoteToLab } from "../../lib/labs";
import { LAB_HOME_DOC_ID } from "../../lib/route";
import {
  useCreateSubject,
  useDeleteSubject,
  useSubjects,
  useUpdateSubject,
} from "../../lib/subjects";
import { pushRoute, replaceRoute, useRoute } from "../../lib/useRoute";
import { useDialogs } from "../ui/DialogProvider";
import { Identicon } from "./Identicon";
import { SubjectWorkspace } from "./SubjectWorkspace";
import "./SubjectLayout.css";

/**
 * 좌측 얇은 아이콘 레일 + 활성 주제의 SubjectWorkspace + 주제 관리 모달.
 *
 * 레일 상단 rail-subjects = **개인 주제(personal)만** 빠른 전환. 연구실(LAB)은 주제 레일에서 분리 —
 * 하단 rail-future의 **연구실 버튼 팝오버**에서 목록 진입·코드 가입·주제 승격을 한다. 연구실을 고르면
 * 연구실 홈(lab-home 대시보드)이 메인에 뜬다(switchSubject가 kind를 보고 doc 분기). rail-future의 나머지
 * 전역 도구: 장비(활성 LAB일 때), 학습 정리(준비 중). 주제 관리 모달(⋯)은 전체 백오피스(이름변경·삭제).
 *
 * 주제 전환 시 X-Laminar-Subject-Id 헤더 변경 + tabs/graph 캐시 제거 + key 리마운트.
 * DX-3: 활성 주제의 정본은 URL(/s/{subjectId}) — localStorage는 API 헤더용 follower.
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
  // 연구실 팝오버 — 위치(버튼 우하단 rect)를 담아 fixed로 띄운다. null=닫힘.
  // .rail이 overflow-y:auto라 가로도 clip → absolute 팝오버는 레일 밖에서 잘린다(툴팁처럼 fixed로 회피).
  const [labPop, setLabPop] = useState<{ x: number; y: number } | null>(null);
  // '주제 본문' 신호 — 증가시키면 활성 주제의 SubjectWorkspace가 본문 문서를 연다(레일 ▤ 버튼).
  const [bodyNonce, setBodyNonce] = useState(0);
  // '장비 관리' 신호 — 증가시키면 SubjectWorkspace가 장비 doctab 창을 연다(레일 플라스크 버튼).
  const [equipmentNonce, setEquipmentNonce] = useState(0);
  const [hoverTip, setHoverTip] = useState<{ name: string; y: number } | null>(null);

  const list = subjects.data ?? [];
  const personalList = list.filter((s) => s.kind === "PERSONAL");
  const labList = list.filter((s) => s.kind === "LAB");

  function purgeSubjectCaches() {
    // tabs는 이제 subjectId 스코프라 교차오염은 원천 차단되지만(Q6), 구 주제 캐시 메모리 정리를
    // 위해 prefix ["tabs"]로 전 주제 슬롯을 제거한다. tabGraph는 tabId(전역 유일)라 그대로 prefix 제거.
    qc.removeQueries({ queryKey: ["tabs"] });
    qc.removeQueries({ queryKey: dagKeys.tabGraphs });
  }

  // LAB이면 홈(대시보드)을 진입 기본 doc으로, 개인이면 보드 직행(doc=null).
  function entryDocFor(kind: Subject["kind"] | undefined) {
    return kind === "LAB" ? { kind: "lab-home" as const, id: LAB_HOME_DOC_ID } : null;
  }

  // URL(정본) → 활성 주제 보정·동기화. 헤더(setCurrentSubjectId)를 마운트 트리거(setActiveId)보다
  // 먼저 동기 호출해야 자식(useTabs)의 첫 fetch가 새 주제 헤더로 나간다(자식 effect가 부모보다 선행).
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
      const nextKind = subjects.data.find((s) => s.id === next)?.kind;
      replaceRoute({ subjectId: next, tabId: null, view: "canvas", doc: entryDocFor(nextKind) });
    } else if (!next && route.subjectId) {
      replaceRoute({ subjectId: null, tabId: null, view: "canvas", doc: null });
    }
  }, [subjects.data, route.subjectId]);

  function switchSubject(id: string) {
    const target = list.find((s) => s.id === id);
    const doc = entryDocFor(target?.kind);
    if (id === activeId) {
      // 이미 활성: 연구실이면 홈으로 되돌림(작업공간·장비에서 홈 복귀), 개인이면 무시.
      if (doc) pushRoute({ subjectId: id, tabId: route.tabId, view: route.view, doc });
      return;
    }
    setCurrentSubjectId(id); // 헤더 먼저(아래 마운트 트리거보다 선행)
    prevActiveRef.current = id;
    purgeSubjectCaches();
    setActiveId(id);
    pushRoute({ subjectId: id, tabId: null, view: "canvas", doc });
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
    setCurrentSubjectId(null);
    setActiveId(null);
    purgeSubjectCaches();
    await qc.invalidateQueries({ queryKey: dagKeys.subjects });
  }

  /** LAB 승격 — OWNER 전용·비가역. 활성 개인 주제를 대상으로 팝오버에서 호출(/current 기반). */
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

  /** 초대코드로 LAB 가입 신청 — 승인 후 멤버가 되면 연구실 목록에 나타난다. */
  async function onJoinByCode() {
    const code = await dialogs.prompt({ title: "LAB 가입", placeholder: "초대코드 입력 (예: ABCD2345)" });
    if (!code || !code.trim()) return;
    try {
      const outcome = await joinLab.mutateAsync(code.trim());
      await dialogs.alert({
        title: "가입 신청 완료",
        message: `"${outcome.labName}"에 가입을 신청했습니다. 관리자 승인 후 연구실 목록에 나타납니다.`,
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
  const canPromoteActive =
    activeSubject?.kind === "PERSONAL" && activeSubject.ownerUserId === me.data?.userId;

  return (
    <div className="lay">
      <aside className="rail">
        <div className="rail-brand" title="LAMINAR">
          L
        </div>

        <div className="rail-subjects">
          {personalList.map((s) => (
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
          <button
            type="button"
            className="rail-btn"
            onClick={() => setManageOpen(true)}
            title="주제 관리"
          >
            ⋯
          </button>
        </div>

        <div className="rail-future">
          {/* 연구실 — 주제 레일과 분리된 별도 진입(팝오버: 목록·코드 가입·주제 승격). */}
          <div className="rail-lab">
            <button
              type="button"
              className={`rail-tile tool${labPop ? " active" : ""}`}
              onClick={(e) => {
                if (labPop) {
                  setLabPop(null);
                  return;
                }
                const r = e.currentTarget.getBoundingClientRect();
                setLabPop({ x: r.right, y: r.bottom });
              }}
              onMouseEnter={(e) => {
                const r = e.currentTarget.getBoundingClientRect();
                setHoverTip({ name: "연구실", y: r.top + r.height / 2 });
              }}
              onMouseLeave={() => setHoverTip(null)}
              aria-label="연구실"
              aria-haspopup="menu"
              aria-expanded={labPop != null}
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
                <path d="M4 21 V10 L12 4 L20 10 V21" />
                <path d="M3 21 H21" />
                <path d="M10 21 V15 H14 V21" />
              </svg>
            </button>
            {labPop && (
              <>
                <div className="rail-pop-scrim" onClick={() => setLabPop(null)} />
                <div
                  className="rail-popover"
                  role="menu"
                  aria-label="연구실"
                  style={{ left: labPop.x + 10, bottom: window.innerHeight - labPop.y }}
                >
                  <div className="rail-pop-head">내 연구실</div>
                  <ul className="rail-pop-list">
                    {labList.map((lab) => (
                      <li key={lab.id}>
                        <button
                          type="button"
                          className={`rail-pop-item${lab.id === activeId ? " active" : ""}`}
                          onClick={() => {
                            switchSubject(lab.id);
                            setLabPop(null);
                          }}
                        >
                          <Identicon seed={lab.id} size={18} />
                          <span className="rail-pop-item-name">{lab.name}</span>
                        </button>
                      </li>
                    ))}
                    {labList.length === 0 && (
                      <li className="rail-pop-empty">소속된 연구실이 없습니다</li>
                    )}
                  </ul>
                  <div className="rail-pop-sep" />
                  <button
                    type="button"
                    className="rail-pop-action"
                    onClick={() => {
                      setLabPop(null);
                      void onJoinByCode();
                    }}
                  >
                    ⌗ 코드로 가입
                  </button>
                  {canPromoteActive && activeSubject && (
                    <button
                      type="button"
                      className="rail-pop-action"
                      onClick={() => {
                        setLabPop(null);
                        void onPromote(activeSubject);
                      }}
                    >
                      {`↑ '${activeSubject.name}' 승격`}
                    </button>
                  )}
                </div>
              </>
            )}
          </div>

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
            subjectKind={list.find((s) => s.id === activeId)?.kind ?? "PERSONAL"}
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
              <button
                type="button"
                className="subj-x"
                onClick={() => setManageOpen(false)}
                aria-label="닫기"
              >
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
            </footer>
          </div>
        </div>
      )}
    </div>
  );
}
