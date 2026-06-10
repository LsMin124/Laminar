import { useState } from "react";
import type { Card, Group } from "../../lib/graphTypes";
import { useAddCardToGroup, useCreateGroup, useRemoveCardFromGroup } from "../../lib/groups";
import { useDialogs } from "../ui/DialogProvider";
import "./CategoryBar.css";
import "./GroupBar.css";

/**
 * 카드 그룹화 툴바 컨트롤 — 선택 카드를 기존 그룹에 추가/제외(목록 클릭) 또는 새 그룹 생성.
 * 기존엔 기존 그룹 추가 시 이름을 직접 입력해야 했으나(불편) → 목록에서 고른다.
 * 드롭다운 시각 스타일은 CategoryBar(.catbar-*)와 공유.
 */
export function GroupBar({
  tabId,
  groups,
  cards,
  groupMembers,
}: {
  tabId: string;
  groups: Group[];
  /** 선택된 카드들 — 0개면 비활성. */
  cards: Card[];
  /** groupId → 멤버 cardId 목록(멤버십 판정용). */
  groupMembers: Record<string, string[]>;
}) {
  const dialogs = useDialogs();
  const addCardToGroup = useAddCardToGroup(tabId);
  const removeCardFromGroup = useRemoveCardFromGroup(tabId);
  const createGroup = useCreateGroup(tabId);
  const [open, setOpen] = useState(false);

  const disabled = cards.length === 0;

  // 선택 카드의 그룹 소속 — 모두 속하면 all(클릭=제외), 일부면 some(클릭=마저 추가), 없으면 none(클릭=추가).
  function membership(groupId: string): "all" | "some" | "none" {
    const members = new Set(groupMembers[groupId] ?? []);
    const inCount = cards.reduce((n, c) => n + (members.has(c.id) ? 1 : 0), 0);
    if (inCount === 0) return "none";
    return inCount === cards.length ? "all" : "some";
  }

  async function toggleGroup(group: Group) {
    if (cards.length === 0) return;
    const members = new Set(groupMembers[group.id] ?? []);
    if (membership(group.id) === "all") {
      for (const c of cards) {
        await removeCardFromGroup.mutateAsync({ groupId: group.id, cardId: c.id });
      }
    } else {
      for (const c of cards) {
        if (!members.has(c.id)) {
          await addCardToGroup.mutateAsync({ groupId: group.id, cardId: c.id });
        }
      }
    }
    setOpen(false);
  }

  async function onNewGroup() {
    const name = await dialogs.prompt({ title: "새 그룹", placeholder: "그룹 이름" });
    if (!name || !name.trim()) return;
    const group = await createGroup.mutateAsync({ name: name.trim() });
    for (const c of cards) {
      await addCardToGroup.mutateAsync({ groupId: group.id, cardId: c.id });
    }
    setOpen(false);
  }

  return (
    <span className="catbar">
      <button
        type="button"
        className="dag-tool"
        disabled={disabled}
        onClick={() => setOpen((o) => !o)}
        title={
          disabled ? "카드를 선택하면 그룹에 추가할 수 있습니다" : "선택 카드를 그룹에 추가/제외"
        }
      >
        ▣ 그룹화{cards.length > 1 ? ` (${cards.length})` : ""}
      </button>

      {open && !disabled && (
        <>
          <div className="catbar-backdrop" onClick={() => setOpen(false)} />
          <div className="catbar-pop" role="menu">
            <div className="catbar-sec">
              {cards.length === 1 ? "이 카드를 그룹에" : `${cards.length}개 카드를 그룹에`}
            </div>
            {groups.map((g) => {
              const m = membership(g.id);
              return (
                <button
                  key={g.id}
                  type="button"
                  className={`catbar-item${m === "all" ? " active" : ""}`}
                  onClick={() => toggleGroup(g)}
                  title={
                    m === "none"
                      ? "이 그룹에 추가"
                      : m === "all"
                        ? "이 그룹에서 제외"
                        : "나머지도 이 그룹에 추가"
                  }
                >
                  <span className="catbar-sw" style={{ background: g.color ?? "#7c84bf" }} />
                  <span className="gbar-name">{g.name}</span>
                  <span className="gbar-mark">{m === "all" ? "✓" : m === "some" ? "–" : ""}</span>
                </button>
              );
            })}
            {groups.length > 0 && <div className="catbar-divider" />}
            <button type="button" className="catbar-item add" onClick={onNewGroup}>
              ＋ 새 그룹 만들어 추가
            </button>
          </div>
        </>
      )}
    </span>
  );
}
