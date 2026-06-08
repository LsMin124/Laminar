import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getCurrentWorkspaceId, setCurrentWorkspaceId } from "../../lib/api";
import {
  useCreateSubject,
  useDeleteSubject,
  useSubjects,
  useUpdateSubject,
  type Subject,
} from "../../lib/dag";
import { useDialogs } from "../ui/DialogProvider";
import { DagWorkspace } from "./DagWorkspace";
import { EquipmentView } from "./EquipmentView";
import { Identicon } from "./Identicon";
import "./SubjectLayout.css";

/**
 * 좌측 얇은 아이콘 레일(주제 전환) + 활성 주제의 DagWorkspace + 주제 관리 모달.
 * 레일=빠른 전환만, 세부사항(목록·이름변경·생성·삭제)은 별도 모달 창에서.
 * 주제 전환 시 X-Laminar-Subject-Id 헤더 변경 + tabs/graph 캐시 제거 + key 리마운트.
 * 향후 장비 관리·학습 정리는 레일 하단 ghost 타일 자리.
 */
export function SubjectLayout() {
  const subjects = useSubjects();
  const createSubject = useCreateSubject();
  const updateSubject = useUpdateSubject();
  const deleteSubject = useDeleteSubject();
  const dialogs = useDialogs();
  const qc = useQueryClient();
  const [activeId, setActiveId] = useState<string | null>(() => getCurrentWorkspaceId());
  const [manageOpen, setManageOpen] = useState(false);
  // '주제 본문' 신호 — 증가시키면 활성 주제의 DagWorkspace가 본문 문서를 연다(레일 ▤ 버튼).
  const [bodyNonce, setBodyNonce] = useState(0);
  // 메인 영역 모드 — 보드(DagWorkspace) ↔ 장비 관리(EquipmentView). 레일 '장' 타일로 전환.
  const [mode, setMode] = useState<"workspace" | "equipment">("workspace");
  const [hoverTip, setHoverTip] = useState<{ name: string; y: number } | null>(null);

  const list = subjects.data ?? [];

  // 목록 로드 시 활성 주제 보정(저장값 없거나 목록에 없으면 첫 주제) + 헤더 동기화.
  useEffect(() => {
    if (!subjects.data) return;
    const valid = !!activeId && subjects.data.some((s) => s.id === activeId);
    const next = valid ? activeId : (subjects.data[0]?.id ?? null);
    setCurrentWorkspaceId(next);
    if (next !== activeId) setActiveId(next);
  }, [subjects.data, activeId]);

  function switchSubject(id: string) {
    setMode("workspace");
    if (id === activeId) return;
    setCurrentWorkspaceId(id);
    setActiveId(id);
    qc.removeQueries({ queryKey: ["tabs"] });
    qc.removeQueries({ queryKey: ["tabGraph"] });
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
    // 활성 주제가 사라졌으니 헤더/활성 초기화 → 목록 재조회 시 효과가 첫 주제를 재선정.
    setCurrentWorkspaceId(null);
    setActiveId(null);
    qc.removeQueries({ queryKey: ["tabs"] });
    qc.removeQueries({ queryKey: ["tabGraph"] });
    await qc.invalidateQueries({ queryKey: ["subjects"] });
  }

  const activeValid = !!activeId && list.some((s) => s.id === activeId);

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
              onClick={() => {
                setMode("workspace");
                setBodyNonce((n) => n + 1);
              }}
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
          <button
            type="button"
            className={`rail-tile equip${mode === "equipment" ? " active" : ""}`}
            onClick={() => setMode("equipment")}
            disabled={!activeValid}
            title="장비 관리"
            aria-label="장비 관리"
          >
            <svg
              className="rail-icon"
              viewBox="0 0 24 24"
              width="20"
              height="20"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.6"
              strokeLinejoin="miter"
              strokeLinecap="square"
              aria-hidden="true"
            >
              <path d="M9.5 3.5 H14.5" />
              <path d="M10.5 3.5 V9 L4.8 19 H19.2 L13.5 9 V3.5" />
              <path d="M7.4 14.5 H16.6" />
            </svg>
          </button>
          <button type="button" className="rail-tile ghost" disabled title="학습 정리 (준비 중)">
            학
          </button>
        </div>
      </aside>

      <main className="lay-main">
        {activeValid ? (
          <>
            {/* DagWorkspace는 장비 뷰로 전환해도 unmount하지 않고 hidden 처리 — 열린 문서·캔버스 상태와
                bodyNonce 메커니즘을 보존(주제 전환 시에만 key로 리마운트). */}
            <div className="lay-pane" hidden={mode !== "workspace"}>
              <DagWorkspace
                key={activeId}
                subjectId={activeId ?? ""}
                subjectName={list.find((s) => s.id === activeId)?.name ?? ""}
                openSubjectBodyNonce={bodyNonce}
              />
            </div>
            {mode === "equipment" && (
              <EquipmentView
                subjectName={list.find((s) => s.id === activeId)?.name ?? ""}
                onClose={() => setMode("workspace")}
              />
            )}
          </>
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

