import { useState } from "react";
import { useBoardTabs, useCreateTab, useUpdateTab } from "../../lib/queries";
import type { TabResponse } from "../../lib/types";
import "./TabTreeSidebar.css";

interface TabNode {
  tab: TabResponse;
  children: TabNode[];
}

function buildTree(tabs: TabResponse[]): TabNode[] {
  const byParent = new Map<string | null, TabResponse[]>();
  tabs.forEach((t) => {
    const k = t.parentTabId;
    if (!byParent.has(k)) byParent.set(k, []);
    byParent.get(k)!.push(t);
  });
  byParent.forEach((arr) => arr.sort((a, b) => a.priority - b.priority));
  const build = (parentId: string | null): TabNode[] =>
    (byParent.get(parentId) ?? []).map((t) => ({
      tab: t,
      children: build(t.id),
    }));
  return build(null);
}

interface Props {
  boardId: string;
  selectedTabId: string | null;
  onSelectTab: (tabId: string | null) => void;
}

/**
 * 보드 좌측 탭 트리 (P4a) — 중기목표 탭을 트리로 표시·생성·선택. 보드 조직 골격.
 * (선택 탭으로 메인 렌즈를 스코프하는 줌인/아웃은 P4b 후속.)
 */
export function TabTreeSidebar({ boardId, selectedTabId, onSelectTab }: Props) {
  const tabs = useBoardTabs(boardId);
  const createTab = useCreateTab(boardId);
  const updateTab = useUpdateTab(boardId);
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());

  const tree = buildTree(tabs.data ?? []);

  function addTab(parentId: string | null) {
    const name = window.prompt(parentId ? "하위 탭 이름" : "탭 이름");
    if (name?.trim()) {
      createTab.mutate({ name: name.trim(), parentTabId: parentId ?? undefined });
    }
  }

  function toggle(id: string) {
    setCollapsed((s) => {
      const n = new Set(s);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });
  }

  function renderNodes(nodes: TabNode[], depth: number) {
    return (
      <ul className="tab-tree-list">
        {nodes.map((node) => {
          const hasChildren = node.children.length > 0;
          const isCollapsed = collapsed.has(node.tab.id);
          return (
            <li key={node.tab.id}>
              <div
                className={`tab-tree-item${selectedTabId === node.tab.id ? " selected" : ""}${node.tab.visible ? "" : " off"}`}
                style={{ paddingLeft: `${0.4 + depth * 0.85}rem` }}
              >
                {hasChildren ? (
                  <button
                    type="button"
                    className="tab-tree-toggle"
                    onClick={() => toggle(node.tab.id)}
                    aria-label={isCollapsed ? "펼치기" : "접기"}
                  >
                    {isCollapsed ? "▸" : "▾"}
                  </button>
                ) : (
                  <span className="tab-tree-toggle-spacer" />
                )}
                <button
                  type="button"
                  className="tab-tree-label"
                  onClick={() => onSelectTab(node.tab.id)}
                  title={node.tab.name}
                >
                  {node.tab.labelColor && (
                    <span
                      className="tab-tree-dot"
                      style={{ background: node.tab.labelColor }}
                    />
                  )}
                  {node.tab.name}
                </button>
                <button
                  type="button"
                  className={`tab-tree-vis${node.tab.visible ? " on" : ""}`}
                  onClick={() =>
                    updateTab.mutate({
                      tabId: node.tab.id,
                      visible: !node.tab.visible,
                    })
                  }
                  title={
                    node.tab.visible ? "타임라인에서 숨김" : "타임라인에 표시"
                  }
                  aria-pressed={node.tab.visible}
                >
                  {node.tab.visible ? "ON" : "OFF"}
                </button>
                <button
                  type="button"
                  className="tab-tree-add"
                  onClick={() => addTab(node.tab.id)}
                  title="하위 탭 추가"
                >
                  +
                </button>
              </div>
              {hasChildren && !isCollapsed && renderNodes(node.children, depth + 1)}
            </li>
          );
        })}
      </ul>
    );
  }

  return (
    <aside className="tab-tree-sidebar">
      <header className="tab-tree-head">
        <span className="tab-tree-title">탭</span>
        <button
          type="button"
          className="tab-tree-add-root"
          onClick={() => addTab(null)}
        >
          + 탭
        </button>
      </header>
      <div className="tab-tree-scroll">
        <button
          type="button"
          className={`tab-tree-item tab-tree-all${selectedTabId === null ? " selected" : ""}`}
          onClick={() => onSelectTab(null)}
        >
          전체 보기
        </button>
        {tabs.isLoading ? (
          <p className="loading tab-tree-loading">불러오는 중...</p>
        ) : tree.length === 0 ? (
          <p className="tab-tree-empty">탭 없음 — “+ 탭”으로 추가</p>
        ) : (
          renderNodes(tree, 0)
        )}
      </div>
    </aside>
  );
}
