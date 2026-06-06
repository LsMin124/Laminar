import { lazy, Suspense, useState } from "react";
import { api } from "../../lib/api";
import { useCreateTab, useTabs } from "../../lib/dag";
import { useDialogs } from "../ui/DialogProvider";
import { CalendarView } from "./CalendarView";
import { DagCanvas } from "./DagCanvas";
import { Identicon } from "./Identicon";
import "./DagWorkspace.css";

// 본문(마크다운+KaTeX)은 무겁고 항상 쓰진 않으므로 지연 로드 — 초기 번들에서 분리.
const CardBody = lazy(() =>
  import("./CardBody").then((m) => ({ default: m.CardBody })),
);

/**
 * DAG 워크스페이스 셸 — 탭 목록/생성 + 선택 탭의 DAG 캔버스 호스트.
 * (장비·멤버·관리자 등 부차 페이지는 Phase 4 범위에서 제외)
 */
export function DagWorkspace({
  subjectId,
  subjectName,
}: {
  subjectId: string;
  subjectName: string;
}) {
  const tabs = useTabs();
  const createTab = useCreateTab();
  const dialogs = useDialogs();
  const [activeTab, setActiveTab] = useState<string | null>(null);
  const [view, setView] = useState<"canvas" | "calendar">("canvas");
  // 브라우저 탭식 본문 문서 — 열린 카드들 + 활성(activeDoc=null이면 보드 뷰).
  const [openDocs, setOpenDocs] = useState<{ cardId: string; tabId: string; title: string }[]>([]);
  const [activeDoc, setActiveDoc] = useState<string | null>(null);

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

  // 카드 본문을 문서 탭으로 열기(이미 열려 있으면 제목 갱신 후 활성화).
  function openCard(cardId: string, title: string) {
    if (!active) return;
    setOpenDocs((prev) =>
      prev.some((d) => d.cardId === cardId)
        ? prev.map((d) => (d.cardId === cardId ? { ...d, title } : d))
        : [...prev, { cardId, tabId: active, title }],
    );
    setActiveDoc(cardId);
  }
  function closeDoc(cardId: string) {
    setOpenDocs((prev) => prev.filter((d) => d.cardId !== cardId));
    setActiveDoc((cur) => (cur === cardId ? null : cur));
  }
  function showBoard(nextView?: "canvas" | "calendar") {
    if (nextView) setView(nextView);
    setActiveDoc(null);
  }

  return (
    <div className="dw">
      <header className="dw-header">
        <div className="dw-subject" title={subjectName}>
          <Identicon seed={subjectId} size={18} />
          <span className="dw-subject-name">{subjectName}</span>
        </div>
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
            className={`dw-view${activeDoc === null && view === "canvas" ? " active" : ""}`}
            onClick={() => showBoard("canvas")}
          >
            캔버스
          </button>
          <button
            type="button"
            className={`dw-view${activeDoc === null && view === "calendar" ? " active" : ""}`}
            onClick={() => showBoard("calendar")}
          >
            캘린더
          </button>
        </div>
        <button type="button" className="dw-logout" onClick={onLogout}>
          로그아웃
        </button>
      </header>
      {openDocs.length > 0 && (
        <nav className="dw-doctabs">
          <button
            type="button"
            className={`dw-doctab${activeDoc === null ? " active" : ""}`}
            onClick={() => setActiveDoc(null)}
          >
            ◧ 보드
          </button>
          {openDocs.map((d) => (
            <span
              key={d.cardId}
              className={`dw-doctab${activeDoc === d.cardId ? " active" : ""}`}
            >
              <button
                type="button"
                className="dw-doctab-label"
                onClick={() => setActiveDoc(d.cardId)}
                title={d.title}
              >
                {d.title || "(제목 없음)"}
              </button>
              <button
                type="button"
                className="dw-doctab-x"
                onClick={() => closeDoc(d.cardId)}
                aria-label="닫기"
              >
                ✕
              </button>
            </span>
          ))}
        </nav>
      )}
      <main className="dw-main">
        {activeDoc ? (
          <Suspense fallback={<div className="dw-empty">본문 불러오는 중...</div>}>
            <CardBody
              key={activeDoc}
              cardId={activeDoc}
              tabId={openDocs.find((d) => d.cardId === activeDoc)?.tabId ?? active ?? ""}
            />
          </Suspense>
        ) : active ? (
          view === "canvas" ? (
            <DagCanvas key={active} tabId={active} onOpenCard={openCard} />
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
