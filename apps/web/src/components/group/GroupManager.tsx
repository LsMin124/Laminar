import { useState } from "react";
import {
  useAddGroupMember,
  useCardRelations,
  useCreateCardRelation,
  useCreateGroup,
  useCreateGroupRelation,
  useDeleteCardRelation,
  useDeleteGroup,
  useDeleteGroupRelation,
  useGroupMembers,
  useGroupRelations,
  useGroups,
  useRemoveGroupMember,
  useUpdateGroup,
} from "../../lib/queries";
import { useDialogs } from "../ui/DialogProvider";
import type { CardResponse } from "../../lib/types";
import "./GroupManager.css";

interface GroupManagerProps {
  boardId: string;
  cards: CardResponse[];
}

export function GroupManager({ boardId, cards }: GroupManagerProps) {
  const dialogs = useDialogs();
  const groups = useGroups(boardId);
  const createGroup = useCreateGroup(boardId);
  const updateGroup = useUpdateGroup(boardId);
  const deleteGroup = useDeleteGroup(boardId);
  const cardRelations = useCardRelations(boardId);
  const groupRelations = useGroupRelations(boardId);
  const createCardRelation = useCreateCardRelation(boardId);
  const deleteCardRelation = useDeleteCardRelation(boardId);
  const createGroupRelation = useCreateGroupRelation(boardId);
  const deleteGroupRelation = useDeleteGroupRelation(boardId);

  const [selectedGroupId, setSelectedGroupId] = useState<string | null>(null);
  const [relationDraft, setRelationDraft] = useState({
    fromCardId: "",
    toCardId: "",
    relationKind: "RELATED",
    summary: "",
  });
  const [groupRelationDraft, setGroupRelationDraft] = useState({
    fromGroupId: "",
    toGroupId: "",
    relationKind: "RELATED",
    summary: "",
  });

  const cardById = new Map(cards.map((c) => [c.id, c]));
  const groupById = new Map((groups.data ?? []).map((g) => [g.id, g]));

  return (
    <div className="group-manager">
      <section className="group-manager-section">
        <header className="group-manager-head">
          <h3>그룹 ({groups.data?.length ?? 0})</h3>
          <button
            type="button"
            onClick={async () => {
              const name = await dialogs.prompt({
                title: "그룹 추가",
                placeholder: "그룹 이름",
              });
              if (!name?.trim()) return;
              const color = await dialogs.prompt({
                title: "그룹 색상",
                placeholder: "#rrggbb (비우면 회색)",
              });
              createGroup.mutate({ name: name.trim(), color: color?.trim() || null });
            }}
          >
            + 그룹
          </button>
        </header>
        <ul className="group-list">
          {groups.data?.map((g) => (
            <li
              key={g.id}
              className={`group-item${selectedGroupId === g.id ? " selected" : ""}`}
              onClick={() =>
                setSelectedGroupId(selectedGroupId === g.id ? null : g.id)
              }
            >
              <span
                className="group-dot"
                style={{ background: g.color ?? "#9ca3af" }}
              />
              <span className="group-name">{g.name}</span>
              <button
                type="button"
                className="group-action"
                onClick={async (e) => {
                  e.stopPropagation();
                  const next = await dialogs.prompt({
                    title: "그룹 이름 변경",
                    defaultValue: g.name,
                  });
                  if (next && next.trim() && next !== g.name) {
                    updateGroup.mutate({ groupId: g.id, name: next.trim() });
                  }
                }}
              >
                ✎
              </button>
              <button
                type="button"
                className="group-action danger"
                onClick={async (e) => {
                  e.stopPropagation();
                  const ok = await dialogs.confirm({
                    title: "그룹 삭제",
                    message: `'${g.name}' 그룹을 삭제할까요?`,
                    confirmLabel: "삭제",
                    danger: true,
                  });
                  if (ok) {
                    deleteGroup.mutate(g.id);
                    if (selectedGroupId === g.id) setSelectedGroupId(null);
                  }
                }}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
        {selectedGroupId && (
          <GroupMemberPanel
            boardId={boardId}
            groupId={selectedGroupId}
            cards={cards}
          />
        )}
      </section>

      <section className="group-manager-section">
        <header className="group-manager-head">
          <h3>카드 관계 ({cardRelations.data?.length ?? 0})</h3>
        </header>
        <div className="relation-form">
          <select
            value={relationDraft.fromCardId}
            onChange={(e) =>
              setRelationDraft((d) => ({ ...d, fromCardId: e.target.value }))
            }
          >
            <option value="">from 카드</option>
            {cards.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
          <select
            value={relationDraft.toCardId}
            onChange={(e) =>
              setRelationDraft((d) => ({ ...d, toCardId: e.target.value }))
            }
          >
            <option value="">to 카드</option>
            {cards.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
          <input
            type="text"
            placeholder="kind"
            value={relationDraft.relationKind}
            onChange={(e) =>
              setRelationDraft((d) => ({
                ...d,
                relationKind: e.target.value,
              }))
            }
          />
          <input
            type="text"
            placeholder="요약 (선택)"
            value={relationDraft.summary}
            onChange={(e) =>
              setRelationDraft((d) => ({ ...d, summary: e.target.value }))
            }
          />
          <button
            type="button"
            className="primary"
            disabled={!relationDraft.fromCardId || !relationDraft.toCardId}
            onClick={async () => {
              if (relationDraft.fromCardId === relationDraft.toCardId) {
                await dialogs.alert({
                  title: "연결 불가",
                  message: "같은 카드는 연결할 수 없습니다.",
                });
                return;
              }
              createCardRelation.mutate({
                ...relationDraft,
                summary: relationDraft.summary || undefined,
              });
              setRelationDraft({
                fromCardId: "",
                toCardId: "",
                relationKind: "RELATED",
                summary: "",
              });
            }}
          >
            연결
          </button>
        </div>
        <ul className="relation-list">
          {cardRelations.data?.map((r) => (
            <li key={r.id} className="relation-item">
              <span>{cardById.get(r.fromCardId)?.title ?? "(삭제됨)"}</span>
              <span className="relation-arrow">→ {r.relationKind} →</span>
              <span>{cardById.get(r.toCardId)?.title ?? "(삭제됨)"}</span>
              {r.summary && <em className="relation-summary">{r.summary}</em>}
              <button
                type="button"
                className="group-action danger"
                onClick={() => deleteCardRelation.mutate(r.id)}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      </section>

      <section className="group-manager-section">
        <header className="group-manager-head">
          <h3>그룹 관계 ({groupRelations.data?.length ?? 0})</h3>
        </header>
        <div className="relation-form">
          <select
            value={groupRelationDraft.fromGroupId}
            onChange={(e) =>
              setGroupRelationDraft((d) => ({
                ...d,
                fromGroupId: e.target.value,
              }))
            }
          >
            <option value="">from 그룹</option>
            {groups.data?.map((g) => (
              <option key={g.id} value={g.id}>
                {g.name}
              </option>
            ))}
          </select>
          <select
            value={groupRelationDraft.toGroupId}
            onChange={(e) =>
              setGroupRelationDraft((d) => ({
                ...d,
                toGroupId: e.target.value,
              }))
            }
          >
            <option value="">to 그룹</option>
            {groups.data?.map((g) => (
              <option key={g.id} value={g.id}>
                {g.name}
              </option>
            ))}
          </select>
          <input
            type="text"
            placeholder="kind"
            value={groupRelationDraft.relationKind}
            onChange={(e) =>
              setGroupRelationDraft((d) => ({
                ...d,
                relationKind: e.target.value,
              }))
            }
          />
          <input
            type="text"
            placeholder="요약 (선택)"
            value={groupRelationDraft.summary}
            onChange={(e) =>
              setGroupRelationDraft((d) => ({
                ...d,
                summary: e.target.value,
              }))
            }
          />
          <button
            type="button"
            className="primary"
            disabled={
              !groupRelationDraft.fromGroupId || !groupRelationDraft.toGroupId
            }
            onClick={async () => {
              if (
                groupRelationDraft.fromGroupId === groupRelationDraft.toGroupId
              ) {
                await dialogs.alert({
                  title: "연결 불가",
                  message: "같은 그룹은 연결할 수 없습니다.",
                });
                return;
              }
              createGroupRelation.mutate({
                ...groupRelationDraft,
                summary: groupRelationDraft.summary || undefined,
              });
              setGroupRelationDraft({
                fromGroupId: "",
                toGroupId: "",
                relationKind: "RELATED",
                summary: "",
              });
            }}
          >
            연결
          </button>
        </div>
        <ul className="relation-list">
          {groupRelations.data?.map((r) => (
            <li key={r.id} className="relation-item">
              <span>{groupById.get(r.fromGroupId)?.name ?? "(삭제됨)"}</span>
              <span className="relation-arrow">→ {r.relationKind} →</span>
              <span>{groupById.get(r.toGroupId)?.name ?? "(삭제됨)"}</span>
              {r.summary && <em className="relation-summary">{r.summary}</em>}
              <button
                type="button"
                className="group-action danger"
                onClick={() => deleteGroupRelation.mutate(r.id)}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

interface GroupMemberPanelProps {
  boardId: string;
  groupId: string;
  cards: CardResponse[];
}

function GroupMemberPanel({ boardId, groupId, cards }: GroupMemberPanelProps) {
  const members = useGroupMembers(groupId);
  const addMember = useAddGroupMember(boardId, groupId);
  const removeMember = useRemoveGroupMember(boardId, groupId);
  const [pendingCardId, setPendingCardId] = useState("");

  const memberIds = new Set(members.data ?? []);
  const memberCards = cards.filter((c) => memberIds.has(c.id));
  const nonMemberCards = cards.filter((c) => !memberIds.has(c.id));

  return (
    <div className="group-member-panel">
      <h4>멤버 ({memberCards.length})</h4>
      <ul className="group-member-list">
        {memberCards.map((c) => (
          <li key={c.id}>
            <span>{c.title}</span>
            <button
              type="button"
              className="group-action danger"
              onClick={() => removeMember.mutate(c.id)}
            >
              ×
            </button>
          </li>
        ))}
      </ul>
      <div className="group-member-add">
        <select
          value={pendingCardId}
          onChange={(e) => setPendingCardId(e.target.value)}
        >
          <option value="">+ 카드 선택</option>
          {nonMemberCards.map((c) => (
            <option key={c.id} value={c.id}>
              {c.title}
            </option>
          ))}
        </select>
        <button
          type="button"
          className="primary"
          disabled={!pendingCardId}
          onClick={() => {
            addMember.mutate(pendingCardId);
            setPendingCardId("");
          }}
        >
          추가
        </button>
      </div>
    </div>
  );
}
