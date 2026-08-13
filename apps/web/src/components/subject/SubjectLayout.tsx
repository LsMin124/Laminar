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
import { ExplorerPanel } from "./ExplorerPanel";
import { Identicon } from "./Identicon";
import { SubjectWorkspace } from "./SubjectWorkspace";
import "./SubjectLayout.css";

/**
 * IDE식 Explorer 트리(좌측) + 활성 주제의 SubjectWorkspace + 주제 관리 모달.
 *
 * 구 아이콘 레일·연구실 팝오버·nonce 신호는 Explorer 트리로 일원화됐다(주제·연구실 루트, 주제
 * 본문/홈/장비/탭/화이트보드/그룹/카드 전부 트리에서 진입 — 문서 열기는 URL 복원 경로 재사용).
 * LAB 승격은 주제 관리 모달로 이동(활성 개인 주제·OWNER 한정, /current 기반).
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

  const list = subjects.data ?? [];

  // 관리 모달 열림 중 ESC로 닫기(키보드 접근성, Q9).
  useEffect(() => {
    if (!manageOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setManageOpen(false);
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [manageOpen]);

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
    const name = await dialogs.prompt({ title: "주제 이름 변경", defaultValue: s.name });
    if (!name || !name.trim() || name.trim() === s.name) return;
    // PATCH /current는 활성 주제 대상 — 취소 시 엉뚱한 전환을 막으려 승인 후 전환한다(Q9).
    if (s.id !== activeId) switchSubject(s.id);
    await updateSubject.mutateAsync({ id: s.id, name: name.trim() });
  }

  async function onDelete(s: Subject) {
    const ok = await dialogs.confirm({
      title: "주제 삭제",
      message: `"${s.name}"와(과) 그 안의 모든 탭·카드·관계가 영구 삭제됩니다. 되돌릴 수 없습니다. 계속할까요?`,
      confirmLabel: "삭제",
      danger: true,
    });
    if (!ok) return;
    // DELETE /current는 활성 주제 대상 — 취소 시 엉뚱한 전환을 막으려 승인 후 전환한다(Q9).
    if (s.id !== activeId) switchSubject(s.id);
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

  /** LAB 승격 — OWNER 전용·비가역. 활성 개인 주제를 대상으로 관리 모달에서 호출(/current 기반). */
  async function onPromote(s: Subject) {
    const ok = await dialogs.confirm({
      title: "LAB으로 승격",
      message: `"${s.name}"을(를) LAB으로 승격합니다. 연구원을 초대해 장비·공지를 공유할 수 있게 되며, 되돌릴 수 없습니다. 계속할까요?`,
      confirmLabel: "승격",
    });
    if (!ok) return;
    if (s.id !== activeId) switchSubject(s.id);
    try {
      await promoteToLab.mutateAsync();
    } catch {
      await dialogs.alert({
        title: "승격 실패",
        message: "LAB으로 승격하지 못했습니다. 소유자만 승격할 수 있습니다.",
      });
    }
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
      <ExplorerPanel
        subjects={list}
        activeId={activeId}
        onSwitchSubject={switchSubject}
        onCreateSubject={() => void onCreateSubject()}
        onJoinByCode={() => void onJoinByCode()}
        onOpenManage={() => setManageOpen(true)}
      />

      <main className="lay-main">
        {activeValid ? (
          <SubjectWorkspace
            key={activeId}
            subjectId={activeId ?? ""}
            subjectName={list.find((s) => s.id === activeId)?.name ?? ""}
            subjectKind={list.find((s) => s.id === activeId)?.kind ?? "PERSONAL"}
          />
        ) : (
          <div className="lay-empty">
            {subjects.isLoading ? "불러오는 중..." : "연구 주제를 만들어 시작하세요 (좌측 ＋ 주제)."}
          </div>
        )}
      </main>

      {manageOpen && (
        <div className="subj-overlay" onClick={() => setManageOpen(false)}>
          <div
            className="subj-modal"
            role="dialog"
            aria-modal="true"
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
              {canPromoteActive && activeSubject && (
                <button
                  type="button"
                  className="subj-create"
                  onClick={() => void onPromote(activeSubject)}
                >
                  {`↑ '${activeSubject.name}' LAB 승격`}
                </button>
              )}
            </footer>
          </div>
        </div>
      )}
    </div>
  );
}
