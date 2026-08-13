import { lazy, Suspense, useEffect, useRef, useState } from "react";
import { api, setCurrentSubjectId } from "../../lib/api";
import { EQUIPMENT_DOC_ID, type DocKind } from "../../lib/route";
import { useCreateTab, useDeleteTab, useTabs } from "../../lib/tabs";
import { pushRoute, replaceRoute, useRoute } from "../../lib/useRoute";
import { useDialogs } from "../ui/DialogProvider";
import { CalendarView } from "../calendar/CalendarView";
import { DagCanvas } from "../dag/DagCanvas";
import { Identicon } from "./Identicon";
import "./SubjectWorkspace.css";

// 본문(마크다운+KaTeX)은 무겁고 항상 쓰진 않으므로 지연 로드 — 초기 번들에서 분리.
const CardBody = lazy(() => import("../doc/CardBody").then((m) => ({ default: m.CardBody })));
const GroupBody = lazy(() => import("../doc/GroupBody").then((m) => ({ default: m.GroupBody })));
const TabBody = lazy(() => import("../doc/TabBody").then((m) => ({ default: m.TabBody })));
const SubjectBody = lazy(() => import("../doc/SubjectBody").then((m) => ({ default: m.SubjectBody })));
const EquipmentView = lazy(() =>
  import("../equipment/EquipmentView").then((m) => ({ default: m.EquipmentView })),
);
const LabDashboard = lazy(() =>
  import("./LabDashboard").then((m) => ({ default: m.LabDashboard })),
);
const WhiteboardCanvas = lazy(() =>
  import("../whiteboard/WhiteboardCanvas").then((m) => ({ default: m.WhiteboardCanvas })),
);

// 열린 문서 — 종류(DocKind)·sentinel은 lib/route와 공유(URL 복원이 같은 어휘를 쓴다).
interface OpenDoc {
  kind: DocKind;
  id: string;
  tabId: string;
  title: string;
}

// 문서 탭 라벨 접두(종류 식별). 카드는 접두 없음.
const DOC_PREFIX: Record<DocKind, string> = {
  card: "",
  group: "▣ ",
  tab: "▭ ",
  subject: "◈ ",
  equipment: "⚗ ",
  "lab-home": "⌂ ",
};

/**
 * DAG 워크스페이스 셸 — 탭 목록/생성 + 선택 탭의 DAG 캔버스 호스트.
 * 본문 문서(카드·그룹·탭·주제)는 상단 브라우저 탭식 doctab 바에서 열린다.
 *
 * DX-3: 활성 탭·뷰·활성 문서의 정본은 URL(useRoute) — 전환은 pushRoute, 보정은 replaceRoute.
 * 열린 문서 "목록"은 세션 상태로 남고, 딥링크/뒤로가기로 URL에만 있는 문서는 복원해 연다.
 */
export function SubjectWorkspace({
  subjectId,
  subjectName,
  subjectKind,
  openSubjectBodyNonce,
  openEquipmentNonce,
}: {
  subjectId: string;
  subjectName: string;
  /** 활성 주제 종별 — personal 주제로의 장비 doctab 딥링크를 차단한다(LAB-P, 런처 게이팅과 짝). */
  subjectKind: "PERSONAL" | "LAB";
  /** 좌측 레일의 '주제 본문' 버튼이 증가시키는 신호 — 변경될 때마다 주제 본문 문서를 연다(마운트 시 제외). */
  openSubjectBodyNonce?: number;
  /** 좌측 레일의 '장비 관리' 버튼이 증가시키는 신호 — 변경될 때마다 장비 doctab을 연다(마운트 시 제외). */
  openEquipmentNonce?: number;
}) {
  const tabs = useTabs();
  const createTab = useCreateTab();
  const deleteTab = useDeleteTab();
  const dialogs = useDialogs();
  const route = useRoute();
  // 열린 문서 목록(세션 상태) — URL에는 활성 문서 하나만 실린다.
  const [openDocs, setOpenDocs] = useState<OpenDoc[]>([]);

  const list = tabs.data ?? [];
  const routeTabValid = !!route.tabId && list.some((t) => t.id === route.tabId);
  const active = routeTabValid ? route.tabId : (list[0]?.id ?? null);
  const view = route.view;
  const activeDoc = route.doc?.id ?? null;
  const activeTab = list.find((t) => t.id === active) ?? null;

  // URL의 탭이 목록에 없으면(삭제·타 주제 딥링크) 첫 탭으로 보정 — replace(히스토리 오염 방지).
  // 주제 전환 과도 구간(뒤로가기로 URL은 새 주제, 본 컴포넌트는 아직 구 주제) 동안
  // 남의 URL을 내 탭으로 되돌려 쓰지 않도록 자기 주제의 URL에만 반응한다.
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

  /** 탭·화이트보드는 별도 기능 — 생성 동선을 버튼부터 분리한다(종류 선택 confirm 제거, 사용자 피드백). */
  async function onCreateTab(kind: "DAG" | "WHITEBOARD") {
    const name = await dialogs.prompt({
      title: kind === "WHITEBOARD" ? "새 화이트보드" : "새 탭",
      placeholder: kind === "WHITEBOARD" ? "화이트보드 이름" : "탭 이름",
    });
    if (!name || !name.trim()) return;
    const tab = await createTab.mutateAsync({ name: name.trim(), kind });
    pushRoute({ subjectId, tabId: tab.id, view, doc: null });
  }

  /** 탭 삭제 — 확인 후 soft delete. 활성 탭이 사라지면 URL 보정 effect가 첫 탭으로 이동시킨다. */
  async function onDeleteTab(tabId: string, name: string, kind: string) {
    const noun = kind === "WHITEBOARD" ? "화이트보드" : "탭";
    const ok = await dialogs.confirm({
      title: `${noun} 삭제`,
      message: `"${name}" ${noun}을(를) 삭제할까요? 안의 내용도 함께 사라집니다.`,
      confirmLabel: "삭제",
    });
    if (!ok) return;
    deleteTab.mutate(tabId);
    setOpenDocs((prev) => prev.filter((d) => !(d.kind === "tab" && d.id === tabId)));
  }

  async function onLogout() {
    try {
      await api.post("/api/auth/logout");
    } catch {
      // 무시 — 어차피 로컬 상태를 비우고 새로고침한다.
    }
    setCurrentSubjectId(null); // 활성 주제 선택 해제(신·구 localStorage 키 모두 정리)
    location.reload();
  }

  // 본문 문서 열기(이미 열려 있으면 제목 갱신) + URL 활성화. 탭/주제 본문은 활성 보드 탭이 없어도 열 수 있다.
  function openDoc(kind: DocKind, id: string, title: string) {
    setOpenDocs((prev) =>
      prev.some((d) => d.id === id)
        ? prev.map((d) => (d.id === id ? { ...d, title } : d))
        : [...prev, { kind, id, tabId: active ?? "", title }],
    );
    pushRoute({ subjectId, tabId: route.tabId, view, doc: { kind, id } });
  }
  const openCard = (cardId: string, title: string) => openDoc("card", cardId, title);
  const openGroup = (groupId: string, title: string) => openDoc("group", groupId, title);
  const openTab = (tabId: string, title: string) => openDoc("tab", tabId, title);
  const openSubject = () => openDoc("subject", subjectId, subjectName);
  // 좌측 레일에서 주제 본문 열기 — nonce가 바뀔 때만(마운트·주제 전환 리마운트 시엔 열지 않음).
  // latest-ref는 커밋 후 대입(아래 nonce effect보다 선언이 앞이라 같은 커밋에서 먼저 갱신됨).
  const openSubjectRef = useRef(openSubject);
  useEffect(() => {
    openSubjectRef.current = openSubject;
  });
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
    if (route.doc?.id === id) {
      pushRoute({ subjectId, tabId: route.tabId, view, doc: null });
    }
  }
  function showBoard(nextView?: "canvas" | "calendar") {
    pushRoute({ subjectId, tabId: route.tabId, view: nextView ?? view, doc: null });
  }
  function activateDoc(d: OpenDoc) {
    pushRoute({ subjectId, tabId: route.tabId, view, doc: { kind: d.kind, id: d.id } });
  }
  // 장비 관리를 브라우저 탭식 문서로 열기(카드/본문과 동일한 doctab 창).
  const openEquipment = () => openDoc("equipment", EQUIPMENT_DOC_ID, "장비 관리");
  // 좌측 레일에서 장비 열기 — nonce가 바뀔 때만(마운트·주제 전환 리마운트 시엔 열지 않음).
  const openEquipmentRef = useRef(openEquipment);
  useEffect(() => {
    openEquipmentRef.current = openEquipment;
  });
  const firstEquipNonceRun = useRef(true);
  useEffect(() => {
    if (firstEquipNonceRun.current) {
      firstEquipNonceRun.current = false;
      return;
    }
    openEquipmentRef.current();
  }, [openEquipmentNonce]);

  // URL에만 있는 활성 문서 복원(딥링크·닫은 문서로 뒤로가기) — 제목은 단건 조회로 보정.
  // latest-ref 패턴: 본체는 매 커밋 갱신, 실행은 문서 키가 바뀔 때만.
  const routeDocKey = route.doc ? `${route.doc.kind}:${route.doc.id}` : null;
  const restoreDocRef = useRef<() => void>(() => {});
  useEffect(() => {
    restoreDocRef.current = () => {
      if (route.subjectId !== subjectId) return; // 주제 전환 과도 구간 — 남의 URL 문서를 열지 않음
      const doc = route.doc;
      if (!doc) return;
      if ((doc.kind === "equipment" || doc.kind === "lab-home") && subjectKind !== "LAB") {
        // 장비·연구실 홈은 lab 전용(L3) — personal 주제로의 딥링크는 열지 않고 URL만 보드로 보정.
        replaceRoute({ subjectId, tabId: route.tabId, view, doc: null });
        return;
      }
      if (openDocs.some((d) => d.id === doc.id)) return; // 이미 열려 있음 — 복원 불요
      const title =
        doc.kind === "equipment"
          ? "장비 관리"
          : doc.kind === "lab-home"
            ? "연구실 홈"
            : doc.kind === "subject"
              ? subjectName
              : doc.kind === "tab"
                ? (list.find((t) => t.id === doc.id)?.name ?? "")
                : "";
      setOpenDocs((prev) =>
        prev.some((d) => d.id === doc.id)
          ? prev
          : [...prev, { kind: doc.kind, id: doc.id, tabId: active ?? "", title }],
      );
      if (doc.kind === "card" || doc.kind === "group") {
        const path = doc.kind === "card" ? `/api/cards/${doc.id}` : `/api/groups/${doc.id}`;
        api.get<{ title?: string; name?: string }>(path).then(
          (data) => {
            const fetched = data.title ?? data.name ?? "";
            setOpenDocs((prev) =>
              prev.map((d) => (d.id === doc.id && !d.title ? { ...d, title: fetched } : d)),
            );
          },
          () => {
            // 부재/권한 없음은 본문 영역이 에러를 표시 — 라벨은 빈 채 둔다.
          },
        );
      }
    };
  });
  useEffect(() => {
    if (routeDocKey) restoreDocRef.current();
  }, [routeDocKey]);

  // 복원된 탭 doc의 빈 제목을 탭 목록 도착 시 보정.
  useEffect(() => {
    if (!tabs.data) return;
    const data = tabs.data;
    setOpenDocs((prev) => {
      let changed = false;
      const next = prev.map((d) => {
        if (d.kind !== "tab" || d.title) return d;
        const name = data.find((t) => t.id === d.id)?.name;
        if (!name) return d;
        changed = true;
        return { ...d, title: name };
      });
      return changed ? next : prev;
    });
  }, [tabs.data]);

  const activeDocEntry = openDocs.find((d) => d.id === activeDoc) ?? null;

  function renderDoc(d: OpenDoc) {
    switch (d.kind) {
      case "lab-home":
        return <LabDashboard key={d.id} subjectId={subjectId} subjectName={subjectName} />;
      case "equipment":
        return (
          <EquipmentView
            key={d.id}
            subjectId={subjectId}
            onClose={() => closeDoc(EQUIPMENT_DOC_ID)}
          />
        );
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
                  title={t.kind === "WHITEBOARD" ? "화이트보드" : undefined}
                  onClick={() => pushRoute({ subjectId, tabId: t.id, view, doc: route.doc })}
                >
                  {t.kind === "WHITEBOARD" && <span className="dw-tab-glyph">▦</span>}
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
                {isActive && (
                  <button
                    type="button"
                    className="dw-tab-x"
                    onClick={() => void onDeleteTab(t.id, t.name, t.kind ?? "DAG")}
                    title="삭제"
                    aria-label="탭 삭제"
                  >
                    ✕
                  </button>
                )}
              </span>
            );
          })}
          <button type="button" className="dw-tab-add" onClick={() => void onCreateTab("DAG")}>
            + 탭
          </button>
          <button
            type="button"
            className="dw-tab-add wb"
            onClick={() => void onCreateTab("WHITEBOARD")}
          >
            ▦ + 화이트보드
          </button>
        </nav>
        {activeTab?.kind !== "WHITEBOARD" && (
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
        )}
        <button type="button" className="dw-logout" onClick={onLogout}>
          로그아웃
        </button>
      </header>
      {openDocs.length > 0 && (
        <nav className="dw-doctabs">
          <button
            type="button"
            className={`dw-doctab${activeDoc === null ? " active" : ""}`}
            onClick={() => showBoard()}
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
                onClick={() => activateDoc(d)}
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
          activeTab?.kind === "WHITEBOARD" ? (
            <Suspense fallback={<div className="dw-empty">불러오는 중...</div>}>
              <WhiteboardCanvas key={active} tabId={active} />
            </Suspense>
          ) : view === "canvas" ? (
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
