import { lazy, Suspense, useEffect } from "react";
import { api, setCurrentSubjectId } from "../../lib/api";
import { useTabs } from "../../lib/tabs";
import { pushRoute, replaceRoute, useRoute } from "../../lib/useRoute";
import { CalendarView } from "../calendar/CalendarView";
import { DagCanvas } from "../dag/DagCanvas";
import { DocPanel } from "./DocPanel";
import { Identicon } from "./Identicon";
import "./SubjectWorkspace.css";

// 무겁거나 항상 쓰지 않는 화면은 지연 로드 — 초기 번들에서 분리.
const EquipmentView = lazy(() =>
  import("../equipment/EquipmentView").then((m) => ({ default: m.EquipmentView })),
);
const LabDashboard = lazy(() =>
  import("./LabDashboard").then((m) => ({ default: m.LabDashboard })),
);
const WhiteboardCanvas = lazy(() =>
  import("../whiteboard/WhiteboardCanvas").then((m) => ({ default: m.WhiteboardCanvas })),
);

/**
 * 워크스페이스 셸 — 좌측 Explorer가 구조 탐색을 맡고, 여기는 활성 탭의 공간 뷰(캔버스/캘린더/
 * 화이트보드)와 우측 문서 패널(DocPanel)을 담당한다. doctab(다중 열림 문서) 개념은 제거 —
 * URL의 doc 하나가 곧 열린 문서이며 패널로 표시된다(단순화 A안: 트리=구조·캔버스=공간·패널=상세).
 * 장비·연구실 홈은 문서가 아니라 화면이라 패널 대신 메인 전체를 차지한다.
 *
 * DX-3: 활성 탭·뷰·문서의 정본은 URL(useRoute) — 전환은 pushRoute, 보정은 replaceRoute.
 */
export function SubjectWorkspace({
  subjectId,
  subjectName,
  subjectKind,
}: {
  subjectId: string;
  subjectName: string;
  /** 활성 주제 종별 — personal 주제로의 장비·홈 딥링크를 차단한다(LAB-P). */
  subjectKind: "PERSONAL" | "LAB";
}) {
  const tabs = useTabs();
  const route = useRoute();

  const list = tabs.data ?? [];
  const routeTabValid = !!route.tabId && list.some((t) => t.id === route.tabId);
  const active = routeTabValid ? route.tabId : (list[0]?.id ?? null);
  const view = route.view;
  const doc = route.doc;
  const activeTab = list.find((t) => t.id === active) ?? null;

  // URL의 탭이 목록에 없으면(삭제·타 주제 딥링크) 첫 탭으로 보정 — replace(히스토리 오염 방지).
  // 주제 전환 과도 구간 동안 남의 URL을 내 탭으로 되돌려 쓰지 않도록 자기 주제의 URL에만 반응한다.
  useEffect(() => {
    if (route.subjectId !== subjectId) return;
    if (!tabs.data) return;
    if (route.tabId && !tabs.data.some((t) => t.id === route.tabId)) {
      replaceRoute({
        subjectId,
        tabId: tabs.data[0]?.id ?? null,
        view: route.view,
        doc: route.doc,
      });
    }
  }, [tabs.data, route.tabId, route.view, route.doc, route.subjectId, subjectId]);

  // 장비·연구실 홈은 lab 전용(L3) — personal 주제로의 딥링크는 열지 않고 URL만 보드로 보정.
  useEffect(() => {
    if (route.subjectId !== subjectId || !doc) return;
    if ((doc.kind === "equipment" || doc.kind === "lab-home") && subjectKind !== "LAB") {
      replaceRoute({ subjectId, tabId: route.tabId, view, doc: null });
    }
  }, [doc, route.subjectId, route.tabId, subjectId, subjectKind, view]);

  async function onLogout() {
    try {
      await api.post("/api/auth/logout");
    } catch {
      // 무시 — 어차피 로컬 상태를 비우고 새로고침한다.
    }
    setCurrentSubjectId(null); // 활성 주제 선택 해제(신·구 localStorage 키 모두 정리)
    location.reload();
  }

  function showBoard(nextView?: "canvas" | "calendar") {
    pushRoute({ subjectId, tabId: route.tabId, view: nextView ?? view, doc: null });
  }
  const openCard = (cardId: string) =>
    pushRoute({ subjectId, tabId: route.tabId, view, doc: { kind: "card", id: cardId } });
  const openGroup = (groupId: string) =>
    pushRoute({ subjectId, tabId: route.tabId, view, doc: { kind: "group", id: groupId } });

  // 장비·홈=전체 화면(문서가 아니라 화면), 그 외 doc=우측 패널.
  const fullDoc =
    doc && (doc.kind === "equipment" || doc.kind === "lab-home") && subjectKind === "LAB"
      ? doc
      : null;
  const panelDoc =
    doc && !fullDoc && doc.kind !== "equipment" && doc.kind !== "lab-home" ? doc : null;

  return (
    <div className="dw">
      <header className="dw-header">
        <div className="dw-subject" title={subjectName}>
          <Identicon seed={subjectId} size={18} />
          <span className="dw-subject-name">{subjectName}</span>
        </div>
        {/* 보드 전환·생성·삭제는 좌측 Explorer 트리로 일원화 — 헤더엔 현재 탭 이름만 표시. */}
        {activeTab && (
          <div className="dw-current" title={activeTab.name}>
            {activeTab.kind === "WHITEBOARD" && <span className="dw-tab-glyph">▦</span>}
            {activeTab.name}
          </div>
        )}
        {activeTab?.kind !== "WHITEBOARD" && !fullDoc && (
          <div className="dw-views">
            <button
              type="button"
              className={`dw-view${view === "canvas" ? " active" : ""}`}
              onClick={() => showBoard("canvas")}
            >
              캔버스
            </button>
            <button
              type="button"
              className={`dw-view${view === "calendar" ? " active" : ""}`}
              onClick={() => showBoard("calendar")}
            >
              캘린더
            </button>
          </div>
        )}
        <button type="button" className="dw-logout" onClick={onLogout}>
          로그아웃
        </button>
      </header>
      <main className="dw-main">
        {fullDoc ? (
          <Suspense fallback={<div className="dw-empty">불러오는 중...</div>}>
            {fullDoc.kind === "lab-home" ? (
              <LabDashboard subjectId={subjectId} subjectName={subjectName} />
            ) : (
              <EquipmentView subjectId={subjectId} onClose={() => showBoard()} />
            )}
          </Suspense>
        ) : (
          <>
            <div className="dw-stage">
              {active ? (
                activeTab?.kind === "WHITEBOARD" ? (
                  <Suspense fallback={<div className="dw-empty">불러오는 중...</div>}>
                    <WhiteboardCanvas key={active} tabId={active} />
                  </Suspense>
                ) : view === "canvas" ? (
                  <DagCanvas
                    key={active}
                    tabId={active}
                    onOpenCard={openCard}
                    onOpenGroup={openGroup}
                  />
                ) : (
                  <CalendarView key={active} tabId={active} />
                )
              ) : (
                <div className="dw-empty">
                  {tabs.isLoading
                    ? "불러오는 중..."
                    : "탭이 없습니다. 왼쪽 탐색기에서 ＋ 탭 또는 ＋ 화이트보드로 만들어 보세요."}
                </div>
              )}
            </div>
            {panelDoc && (
              <DocPanel
                key={`${panelDoc.kind}:${panelDoc.id}`}
                kind={panelDoc.kind}
                id={panelDoc.id}
                tabId={active ?? ""}
                subjectName={subjectName}
                tabName={list.find((t) => t.id === panelDoc.id)?.name ?? null}
                onClose={() => showBoard()}
              />
            )}
          </>
        )}
      </main>
    </div>
  );
}
