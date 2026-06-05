import { useEffect, useState, type CSSProperties } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getCurrentWorkspaceId, setCurrentWorkspaceId } from "../../lib/api";
import {
  useCreateSubject,
  useSubjects,
  useUpdateSubject,
  type Subject,
} from "../../lib/dag";
import { useDialogs } from "../ui/DialogProvider";
import { DagWorkspace } from "./DagWorkspace";
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
  const dialogs = useDialogs();
  const qc = useQueryClient();
  const [activeId, setActiveId] = useState<string | null>(() => getCurrentWorkspaceId());
  const [manageOpen, setManageOpen] = useState(false);

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
    await updateSubject.mutateAsync(name.trim());
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
              style={{ "--tile-hue": tileHue(s.id) } as CSSProperties}
              onClick={() => switchSubject(s.id)}
              title={s.name}
            >
              {tileGlyph(s.name)}
            </button>
          ))}
          <button type="button" className="rail-btn" onClick={onCreateSubject} title="새 주제">
            ＋
          </button>
          <button type="button" className="rail-btn" onClick={() => setManageOpen(true)} title="주제 관리">
            ⋯
          </button>
        </div>

        <div className="rail-future">
          <button type="button" className="rail-tile ghost" disabled title="장비 관리 (준비 중)">
            장
          </button>
          <button type="button" className="rail-tile ghost" disabled title="학습 정리 (준비 중)">
            학
          </button>
        </div>
      </aside>

      <main className="lay-main">
        {activeValid ? (
          <DagWorkspace key={activeId} />
        ) : (
          <div className="lay-empty">
            {subjects.isLoading ? "불러오는 중..." : "연구 주제를 만들어 시작하세요 (좌측 ＋)."}
          </div>
        )}
      </main>

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
                    <span className="subj-dot" style={{ "--tile-hue": tileHue(s.id) } as CSSProperties} />
                    {s.name}
                  </button>
                  <button type="button" className="subj-act" onClick={() => onRename(s)}>
                    이름변경
                  </button>
                  <button type="button" className="subj-act" disabled title="삭제는 다음 단계에서 지원">
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

/** 주제 이름 첫 글자(타일 글리프). 비면 가운뎃점. */
function tileGlyph(name: string): string {
  const ch = name.trim().charAt(0);
  return ch ? ch.toUpperCase() : "·";
}

/** 주제 id에서 식별용 hue 파생(차분한 한색 계열). 정식 디자인 적용 전 임시. */
function tileHue(id: string): number {
  let h = 0;
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) % 360;
  return h;
}
