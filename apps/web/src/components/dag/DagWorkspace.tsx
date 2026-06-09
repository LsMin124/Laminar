import { lazy, Suspense, useEffect, useRef, useState } from "react";
import { api } from "../../lib/api";
import { useCreateTab, useTabs } from "../../lib/dag";
import { useDialogs } from "../ui/DialogProvider";
import { CalendarView } from "./CalendarView";
import { DagCanvas } from "./DagCanvas";
import { Identicon } from "./Identicon";
import "./DagWorkspace.css";

// 본문(마크다운+KaTeX)은 무겁고 항상 쓰진 않으므로 지연 로드 — 초기 번들에서 분리.
const CardBody = lazy(() => import("./CardBody").then((m) => ({ default: m.CardBody })));
const GroupBody = lazy(() => import("./GroupBody").then((m) => ({ default: m.GroupBody })));
const TabBody = lazy(() => import("./TabBody").then((m) => ({ default: m.TabBody })));
const SubjectBody = lazy(() => import("./SubjectBody").then((m) => ({ default: m.SubjectBody })));
const EquipmentView = lazy(() =>
  import("./EquipmentView").then((m) => ({ default: m.EquipmentView })),
);

// 열린 문서 — 카드·그룹·탭·주제(UUID 키) + 장비(공용 자원, 싱글톤 doc).
type DocKind = "card" | "group" | "tab" | "subject" | "equipment";
interface OpenDoc {
  kind: DocKind;
  id: string;
  tabId: string;
  title: string;
}

// 장비 doc는 단일(주제/탭 무관)이라 고정 sentinel id 사용(UUID와 충돌 없음).
const EQUIPMENT_DOC_ID = "equipment";

// 문서 탭 라벨 접두(종류 식별). 카드는 접두 없음.
const DOC_PREFIX: Record<DocKind, string> = {
  card: "",
  group: "▣ ",
  tab: "▭ ",
  subject: "◈ ",
  equipment: "⚗ ",
};

/**
 * DAG 워크스페이스 셸 — 탭 목록/생성 + 선택 탭의 DAG 캔버스 호스트.
 * 본문 문서(카드·그룹·탭·주제)는 상단 브라우저 탭식 doctab 바에서 열린다.
 */
export function DagWorkspace({
  subjectId,
  subjectName,
  openSubjectBodyNonce,
}: {
  subjectId: string;
  subjectName: string;
  /** 좌측 레일의 '주제 본문' 버튼이 증가시키는 신호 — 변경될 때마다 주제 본문 문서를 연다(마운트 시 제외). */
  openSubjectBodyNonce?: number;
}) {
  const tabs = useTabs();
  const createTab = useCreateTab();
  const dialogs = useDialogs();
  const [activeTab, setActiveTab] = useState<string | null>(null);
  const [view, setView] = useState<"canvas" | "calendar">("canvas");
  // 브라우저 탭식 본문 문서 — 열린 문서들 + 활성(activeDoc=null이면 보드 뷰).
  const [openDocs, setOpenDocs] = useState<OpenDoc[]>([]);
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

  // 본문 문서 열기(이미 열려 있으면 제목 갱신 후 활성화). 탭/주제 본문은 활성 보드 탭이 없어도 열 수 있다.
  function openDoc(kind: DocKind, id: string, title: string) {
    setOpenDocs((prev) =>
      prev.some((d) => d.id === id)
        ? prev.map((d) => (d.id === id ? { ...d, title } : d))
        : [...prev, { kind, id, tabId: active ?? "", title }],
    );
    setActiveDoc(id);
  }
  const openCard = (cardId: string, title: string) => openDoc("card", cardId, title);
  const openGroup = (groupId: string, title: string) => openDoc("group", groupId, title);
  const openTab = (tabId: string, title: string) => openDoc("tab", tabId, title);
  const openSubject = () => openDoc("subject", subjectId, subjectName);
  // 좌측 레일에서 주제 본문 열기 — nonce가 바뀔 때만(마운트·주제 전환 리마운트 시엔 열지 않음).
  const openSubjectRef = useRef(openSubject);
  openSubjectRef.current = openSubject;
  const firstNonceRun = useRef(true);
  useEffect(() => {
    if (firstNonceRun.current) {
      firstNonceRun.current = false;
      return;
    }
    openSubjectRef.current();
  }, [openSubjectBodyNonce]);
  function closeDoc(id: string) {
    setOpenDocs((prev) => prev.filter((d) => d.id !== id));
    setActiveDoc((cur) => (cur === id ? null : cur));
  }
  function showBoard(nextView?: "canvas" | "calendar") {
    if (nextView) setView(nextView);
    setActiveDoc(null);
  }
  // 장비 관리를 브라우저 탭식 문서로 열기(카드/본문과 동일한 doctab 창).
  const openEquipment = () => openDoc("equipment", EQUIPMENT_DOC_ID, "장비 관리");

  const activeDocEntry = openDocs.find((d) => d.id === activeDoc) ?? null;

  function renderDoc(d: OpenDoc) {
    switch (d.kind) {
      case "equipment":
        return <EquipmentView key={d.id} onClose={() => closeDoc(EQUIPMENT_DOC_ID)} />;
      case "subject":
        return <SubjectBody key={d.id} subjectId={d.id} />;
      case "tab":
        return <TabBody key={d.id} tabId={d.id} />;
      case "group":
        return <GroupBody key={d.id} groupId={d.id} tabId={d.tabId} />;
      default:
        return <CardBody key={d.id} cardId={d.id} tabId={d.tabId} />;
    }
  }

  return (
    <div className="dw">
      <header className="dw-header">
        <div className="dw-subject" title={subjectName}>
          <Identicon seed={subjectId} size={18} />
          <span className="dw-subject-name">{subjectName}</span>
        </div>
        <nav className="dw-tabs">
          {list.map((t) => {
            const isActive = t.id === active;
            return (
              <span key={t.id} className={`dw-tab-wrap${isActive ? " active" : ""}`}>
                <button
                  type="button"
                  className={`dw-tab${isActive ? " active" : ""}`}
                  onClick={() => setActiveTab(t.id)}
                >
                  {t.name}
                </button>
                {isActive && (
                  <button
                    type="button"
                    className="dw-tab-doc"
                    onClick={() => openTab(t.id, t.name)}
                    title="탭 본문 열기"
                    aria-label="탭 본문"
                  >
                    ▤
                  </button>
                )}
              </span>
            );
          })}
          <button type="button" className="dw-tab-add" onClick={onCreateTab}>
            + 탭
          </button>
          {/* 장비(공용 자원)는 카드/본문처럼 브라우저 탭식 문서로 열린다(보드 탭 아님). 여기는 그 런처. */}
          <button
            type="button"
            className="dw-tab-equip"
            onClick={openEquipment}
            title="장비 관리 열기 (공용 자원)"
          >
            <svg
              className="dw-tab-equip-icon"
              viewBox="0 0 24 24"
              width="15"
              height="15"
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
            장비
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
              key={d.id}
              className={`dw-doctab${activeDoc === d.id ? " active" : ""} ${d.kind}`}
            >
              <button
                type="button"
                className="dw-doctab-label"
                onClick={() => setActiveDoc(d.id)}
                title={d.title}
              >
                {DOC_PREFIX[d.kind]}
                {d.title || "(제목 없음)"}
              </button>
              <button
                type="button"
                className="dw-doctab-x"
                onClick={() => closeDoc(d.id)}
                aria-label="닫기"
              >
                ✕
              </button>
            </span>
          ))}
        </nav>
      )}
      <main className="dw-main">
        {activeDocEntry ? (
          <Suspense fallback={<div className="dw-empty">불러오는 중...</div>}>
            {renderDoc(activeDocEntry)}
          </Suspense>
        ) : active ? (
          view === "canvas" ? (
            <DagCanvas key={active} tabId={active} onOpenCard={openCard} onOpenGroup={openGroup} />
          ) : (
            <CalendarView key={active} tabId={active} />
          )
        ) : (
          <div className="dw-empty">
            {tabs.isLoading ? "불러오는 중..." : '탭이 없습니다. "+ 탭"으로 만들어 보세요.'}
          </div>
        )}
      </main>
    </div>
  );
}
