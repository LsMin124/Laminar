import { useState } from "react";
import { useTabGraph } from "../../lib/graph";
import type { Subject, Tab } from "../../lib/graphTypes";
import { EQUIPMENT_DOC_ID, LAB_HOME_DOC_ID, type DocKind } from "../../lib/route";
import { useCreateTab, useDeleteTab, useTabs } from "../../lib/tabs";
import { pushRoute, useRoute } from "../../lib/useRoute";
import { useDialogs } from "../ui/DialogProvider";
import { Identicon } from "./Identicon";
import "./ExplorerPanel.css";

/**
 * IDE식 Explorer 트리 — 주제(개인/연구실) → 특수 문서·탭·화이트보드 → 그룹·카드(lazy).
 * 흩어져 있던 진입점(주제 레일·상단 탭 스트립·툴바 생성 버튼)을 한 트리로 일원화한다(사용자 확정).
 * 이동은 전부 URL(pushRoute) — 문서 열기는 SubjectWorkspace의 URL 복원 경로를 그대로 탄다.
 * 활성 주제만 펼친다(API 헤더가 활성 주제 스코프라 타 주제 트리는 전환 후 로드).
 */
export function ExplorerPanel({
  subjects,
  activeId,
  onSwitchSubject,
  onCreateSubject,
  onJoinByCode,
  onOpenManage,
}: {
  subjects: Subject[];
  activeId: string | null;
  onSwitchSubject: (id: string) => void;
  onCreateSubject: () => void;
  onJoinByCode: () => void;
  onOpenManage: () => void;
}) {
  const route = useRoute();
  const tabs = useTabs();
  const createTab = useCreateTab();
  const deleteTab = useDeleteTab();
  const dialogs = useDialogs();
  const [expandedTabs, setExpandedTabs] = useState<ReadonlySet<string>>(new Set<string>());

  const personal = subjects.filter((s) => s.kind === "PERSONAL");
  const labs = subjects.filter((s) => s.kind === "LAB");
  const tabList = tabs.data ?? [];

  function openDoc(kind: DocKind, id: string, tabId?: string) {
    if (!activeId) return;
    pushRoute({
      subjectId: activeId,
      tabId: tabId ?? route.tabId,
      view: route.view,
      doc: { kind, id },
    });
  }

  function goTab(t: Tab) {
    if (!activeId) return;
    pushRoute({ subjectId: activeId, tabId: t.id, view: route.view, doc: null });
  }

  function toggleExpand(tabId: string) {
    setExpandedTabs((prev) => {
      const next = new Set(prev);
      if (next.has(tabId)) next.delete(tabId);
      else next.add(tabId);
      return next;
    });
  }

  async function onCreateTab(kind: "DAG" | "WHITEBOARD") {
    const name = await dialogs.prompt({
      title: kind === "WHITEBOARD" ? "새 화이트보드" : "새 탭",
      placeholder: kind === "WHITEBOARD" ? "화이트보드 이름" : "탭 이름",
    });
    if (!name || !name.trim() || !activeId) return;
    const tab = await createTab.mutateAsync({ name: name.trim(), kind });
    pushRoute({ subjectId: activeId, tabId: tab.id, view: route.view, doc: null });
  }

  async function onDeleteTab(t: Tab) {
    const noun = t.kind === "WHITEBOARD" ? "화이트보드" : "탭";
    const ok = await dialogs.confirm({
      title: `${noun} 삭제`,
      message: `"${t.name}" ${noun}을(를) 삭제할까요? 안의 내용도 함께 사라집니다.`,
      confirmLabel: "삭제",
    });
    if (ok) deleteTab.mutate(t.id);
  }

  function subjectRow(s: Subject) {
    const isActive = s.id === activeId;
    return (
      <div key={s.id} className="exp-branch">
        <button
          type="button"
          className={`exp-row subject${isActive ? " active" : ""}`}
          onClick={() => onSwitchSubject(s.id)}
          title={s.name}
        >
          <span className="exp-caret">{isActive ? "▾" : "▸"}</span>
          <Identicon seed={s.id} size={16} />
          <span className="exp-name">{s.name}</span>
        </button>
        {isActive && (
          <div className="exp-children">
            <button
              type="button"
              className={`exp-row doc${route.doc?.kind === "subject" ? " active" : ""}`}
              onClick={() => openDoc("subject", s.id)}
            >
              <span className="exp-glyph">▤</span> 주제 본문
            </button>
            {s.kind === "LAB" && (
              <>
                <button
                  type="button"
                  className={`exp-row doc${route.doc?.kind === "lab-home" ? " active" : ""}`}
                  onClick={() => openDoc("lab-home", LAB_HOME_DOC_ID)}
                >
                  <span className="exp-glyph">⌂</span> 연구실 홈
                </button>
                <button
                  type="button"
                  className={`exp-row doc${route.doc?.kind === "equipment" ? " active" : ""}`}
                  onClick={() => openDoc("equipment", EQUIPMENT_DOC_ID)}
                >
                  <span className="exp-glyph">⚗</span> 장비 관리
                </button>
              </>
            )}
            {tabList.map((t) => (
              <TabBranch
                key={t.id}
                tab={t}
                isCurrent={route.tabId === t.id && !route.doc}
                expanded={expandedTabs.has(t.id)}
                activeDocId={route.doc?.id ?? null}
                onGo={() => goTab(t)}
                onToggle={() => toggleExpand(t.id)}
                onOpenBody={() => openDoc("tab", t.id, t.id)}
                onOpenGroup={(id) => openDoc("group", id, t.id)}
                onOpenCard={(id) => openDoc("card", id, t.id)}
                onDelete={() => void onDeleteTab(t)}
              />
            ))}
            <div className="exp-adds">
              <button type="button" onClick={() => void onCreateTab("DAG")}>
                ＋ 탭
              </button>
              <button type="button" className="wb" onClick={() => void onCreateTab("WHITEBOARD")}>
                ＋ ▦ 화이트보드
              </button>
            </div>
          </div>
        )}
      </div>
    );
  }

  return (
    <aside className="exp">
      <div className="exp-brand" title="LAMINAR">
        LAMINAR
      </div>
      <div className="exp-tree">
        <div className="exp-sec">개인</div>
        {personal.map(subjectRow)}
        {personal.length === 0 && <div className="exp-empty">개인 주제 없음</div>}
        <div className="exp-sec">연구실</div>
        {labs.map(subjectRow)}
        {labs.length === 0 && <div className="exp-empty">소속 연구실 없음</div>}
      </div>
      <footer className="exp-foot">
        <button type="button" onClick={onCreateSubject} title="새 주제">
          ＋ 주제
        </button>
        <button type="button" onClick={onJoinByCode} title="초대코드로 연구실 가입">
          ⌗ 가입
        </button>
        <button type="button" onClick={onOpenManage} title="주제 관리(이름변경·삭제·승격)">
          ⋯ 관리
        </button>
      </footer>
    </aside>
  );
}

/** 탭 가지 — DAG는 ▸ 펼침(그룹·카드 lazy), 화이트보드는 잎. hover 시 본문(▤)·삭제(✕). */
function TabBranch({
  tab,
  isCurrent,
  expanded,
  activeDocId,
  onGo,
  onToggle,
  onOpenBody,
  onOpenGroup,
  onOpenCard,
  onDelete,
}: {
  tab: Tab;
  isCurrent: boolean;
  expanded: boolean;
  activeDocId: string | null;
  onGo: () => void;
  onToggle: () => void;
  onOpenBody: () => void;
  onOpenGroup: (id: string) => void;
  onOpenCard: (id: string) => void;
  onDelete: () => void;
}) {
  const isWb = tab.kind === "WHITEBOARD";
  return (
    <div className="exp-branch">
      <div className={`exp-row tab${isCurrent ? " active" : ""}`}>
        {isWb ? (
          <span className="exp-caret leaf" />
        ) : (
          <button type="button" className="exp-caret btn" onClick={onToggle} aria-label="펼치기">
            {expanded ? "▾" : "▸"}
          </button>
        )}
        <button type="button" className="exp-label" onClick={onGo} title={tab.name}>
          <span className={`exp-glyph${isWb ? " wb" : ""}`}>{isWb ? "▦" : "▭"}</span>
          <span className="exp-name">{tab.name}</span>
        </button>
        <span className="exp-acts">
          <button type="button" onClick={onOpenBody} title="탭 본문 열기">
            ▤
          </button>
          <button type="button" className="danger" onClick={onDelete} title="삭제">
            ✕
          </button>
        </span>
      </div>
      {expanded && !isWb && (
        <TabChildren
          tabId={tab.id}
          activeDocId={activeDocId}
          onOpenGroup={onOpenGroup}
          onOpenCard={onOpenCard}
        />
      )}
    </div>
  );
}

/** 펼친 탭의 그룹·카드 목록 — 펼칠 때만 마운트되어 그래프를 lazy 조회한다(캔버스와 캐시 공유). */
function TabChildren({
  tabId,
  activeDocId,
  onOpenGroup,
  onOpenCard,
}: {
  tabId: string;
  activeDocId: string | null;
  onOpenGroup: (id: string) => void;
  onOpenCard: (id: string) => void;
}) {
  const graph = useTabGraph(tabId);
  if (graph.isLoading) return <div className="exp-children exp-empty">불러오는 중…</div>;
  const groups = graph.data?.groups ?? [];
  const cards = graph.data?.cards ?? [];
  return (
    <div className="exp-children">
      {groups.map((g) => (
        <button
          key={g.id}
          type="button"
          className={`exp-row doc${activeDocId === g.id ? " active" : ""}`}
          onClick={() => onOpenGroup(g.id)}
          title={g.name}
        >
          <span className="exp-glyph">▣</span>
          <span className="exp-name">{g.name}</span>
        </button>
      ))}
      {cards.map((c) => (
        <button
          key={c.id}
          type="button"
          className={`exp-row doc${activeDocId === c.id ? " active" : ""}`}
          onClick={() => onOpenCard(c.id)}
          title={c.title}
        >
          <span className={`exp-glyph dot${c.completed ? " done" : ""}`}>●</span>
          <span className="exp-name">{c.title || "(제목 없음)"}</span>
        </button>
      ))}
      {groups.length === 0 && cards.length === 0 && <div className="exp-empty">빈 탭</div>}
    </div>
  );
}
