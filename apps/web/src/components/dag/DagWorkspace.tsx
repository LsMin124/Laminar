import { useState } from "react";
import { api } from "../../lib/api";
import { useCreateTab, useTabs } from "../../lib/dag";
import { useDialogs } from "../ui/DialogProvider";
import { CalendarView } from "./CalendarView";
import { DagCanvas } from "./DagCanvas";
import "./DagWorkspace.css";

/**
 * DAG 워크스페이스 셸 — 탭 목록/생성 + 선택 탭의 DAG 캔버스 호스트.
 * (장비·멤버·관리자 등 부차 페이지는 Phase 4 범위에서 제외)
 */
export function DagWorkspace() {
  const tabs = useTabs();
  const createTab = useCreateTab();
  const dialogs = useDialogs();
  const [activeTab, setActiveTab] = useState<string | null>(null);
  const [view, setView] = useState<"canvas" | "calendar">("canvas");

  const list = tabs.data ?? [];
  const active = activeTab ?? list[0]?.id ?? null;

  async function onCreateTab() {
    const name = await dialogs.prompt({ title: "새 탭", placeholder: "탭 이름" });
    if (!name || !name.trim()) return;
    const tab = await createTab.mutateAsync(name.trim());
    setActiveTab(tab.id);
  }

  async function onLogout() {
    try {
      await api.post("/api/auth/logout");
    } catch {
      // 무시 — 어차피 로컬 상태를 비우고 새로고침한다.
    }
    localStorage.removeItem("laminar.workspaceId");
    location.reload();
  }

  return (
    <div className="dw">
      <header className="dw-header">
        <strong className="dw-brand">LAMINAR</strong>
        <nav className="dw-tabs">
          {list.map((t) => (
            <button
              key={t.id}
              type="button"
              className={`dw-tab${t.id === active ? " active" : ""}`}
              onClick={() => setActiveTab(t.id)}
            >
              {t.name}
            </button>
          ))}
          <button type="button" className="dw-tab-add" onClick={onCreateTab}>
            + 탭
          </button>
        </nav>
        <div className="dw-views">
          <button
            type="button"
            className={`dw-view${view === "canvas" ? " active" : ""}`}
            onClick={() => setView("canvas")}
          >
            캔버스
          </button>
          <button
            type="button"
            className={`dw-view${view === "calendar" ? " active" : ""}`}
            onClick={() => setView("calendar")}
          >
            캘린더
          </button>
        </div>
        <button type="button" className="dw-logout" onClick={onLogout}>
          로그아웃
        </button>
      </header>
      <main className="dw-main">
        {active ? (
          view === "canvas" ? (
            <DagCanvas key={active} tabId={active} />
          ) : (
            <CalendarView key={active} tabId={active} />
          )
        ) : (
          <div className="dw-empty">
            {tabs.isLoading
              ? "불러오는 중..."
              : '탭이 없습니다. "+ 탭"으로 만들어 보세요.'}
          </div>
        )}
      </main>
    </div>
  );
}
