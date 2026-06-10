import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getCurrentSubjectId, setCurrentSubjectId } from "../../lib/api";
import type { Subject } from "../../lib/graphTypes";
import { useCreateSubject, useDeleteSubject, useSubjects, useUpdateSubject } from "../../lib/subjects";
import { useDialogs } from "../ui/DialogProvider";
import { SubjectWorkspace } from "./SubjectWorkspace";
import { Identicon } from "./Identicon";
import "./SubjectLayout.css";

/**
 * 좌측 얇은 아이콘 레일(주제 전환) + 활성 주제의 SubjectWorkspace + 주제 관리 모달.
 * 레일=빠른 전환만, 세부사항(목록·이름변경·생성·삭제)은 별도 모달 창에서.
 * 주제 전환 시 X-Laminar-Subject-Id 헤더 변경 + tabs/graph 캐시 제거 + key 리마운트.
 * 레일 하단(rail-future)=전역 도구: 장비 관리(플라스크, doctab 오픈) + 학습 정리(준비 중 ghost).
 */
export function SubjectLayout() {
  const subjects = useSubjects();
  const createSubject = useCreateSubject();
  const updateSubject = useUpdateSubject();
  const deleteSubject = useDeleteSubject();
  const dialogs = useDialogs();
  const qc = useQueryClient();
  const [activeId, setActiveId] = useState<string | null>(() => getCurrentSubjectId());
  const [manageOpen, setManageOpen] = useState(false);
  // '주제 본문' 신호 — 증가시키면 활성 주제의 SubjectWorkspace가 본문 문서를 연다(레일 ▤ 버튼).
  const [bodyNonce, setBodyNonce] = useState(0);
  // '장비 관리' 신호 — 증가시키면 SubjectWorkspace가 장비 doctab 창을 연다(레일 플라스크 버튼).
  const [equipmentNonce, setEquipmentNonce] = useState(0);
  const [hoverTip, setHoverTip] = useState<{ name: string; y: number } | null>(null);

  const list = subjects.data ?? [];

  // 목록 로드 시 활성 주제 보정(저장값 없거나 목록에 없으면 첫 주제) + 헤더 동기화.
  useEffect(() => {
    if (!subjects.data) return;
    const valid = !!activeId && subjects.data.some((s) => s.id === activeId);
    const next = valid ? activeId : (subjects.data[0]?.id ?? null);
    setCurrentSubjectId(next);
    if (next !== activeId) setActiveId(next);
  }, [subjects.data, activeId]);

  function switchSubject(id: string) {
    if (id === activeId) return;
    setCurrentSubjectId(id);
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
    setCurrentSubjectId(null);
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
          {/* 장비 관리(공용 자원) — 전역 도구. 클릭 시 활성 주제의 SubjectWorkspace가 장비 doctab 창을 연다. */}
          {activeValid && (
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

