import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getCurrentWorkspaceId, setCurrentWorkspaceId } from "../../lib/api";
import { useCreateSubject, useSubjects } from "../../lib/dag";
import { useDialogs } from "../ui/DialogProvider";
import { DagWorkspace } from "./DagWorkspace";
import "./SubjectLayout.css";

/**
 * 좌측 주제(연구 주제=워크스페이스) 사이드바 + 활성 주제의 DagWorkspace.
 * 주제 전환 시 X-Laminar-Subject-Id 헤더 변경 + tabs/graph 캐시 제거 + key 리마운트로 새 주제 데이터 로드.
 * 향후 장비 관리·학습 정리 등은 사이드바 하단 영역에 추가.
 */
export function SubjectLayout() {
  const subjects = useSubjects();
  const createSubject = useCreateSubject();
  const dialogs = useDialogs();
  const qc = useQueryClient();
  const [activeId, setActiveId] = useState<string | null>(() => getCurrentWorkspaceId());

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

  const activeValid = !!activeId && list.some((s) => s.id === activeId);

  return (
    <div className="lay">
      <aside className="side">
        <div className="side-brand">LAMINAR</div>

        <div className="side-section">연구 주제</div>
        <nav className="side-list">
          {list.map((s) => (
            <button
              key={s.id}
              type="button"
              className={`side-item${s.id === activeId ? " active" : ""}`}
              onClick={() => switchSubject(s.id)}
              title={s.name}
            >
              {s.name}
            </button>
          ))}
          {subjects.isLoading && <span className="side-muted">불러오는 중...</span>}
          {subjects.data && list.length === 0 && <span className="side-muted">주제 없음</span>}
          <button type="button" className="side-add" onClick={onCreateSubject}>
            + 주제
          </button>
        </nav>

        <div className="side-future">
          <div className="side-section">곧 추가</div>
          <button type="button" className="side-item" disabled>
            장비 관리
          </button>
          <button type="button" className="side-item" disabled>
            학습 정리
          </button>
        </div>
      </aside>

      <main className="lay-main">
        {activeValid ? (
          <DagWorkspace key={activeId} />
        ) : (
          <div className="lay-empty">
            {subjects.isLoading
              ? "불러오는 중..."
              : '연구 주제를 만들어 시작하세요 (좌측 "+ 주제").'}
          </div>
        )}
      </main>
    </div>
  );
}
