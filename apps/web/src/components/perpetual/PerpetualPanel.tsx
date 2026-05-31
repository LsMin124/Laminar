import { useBoardPerpetualNotes, useCreatePerpetualNote } from "../../lib/queries";
import { useDialogs } from "../ui/DialogProvider";
import type { PerpetualNoteResponse } from "../../lib/types";
import "./PerpetualPanel.css";

interface NoteNode {
  note: PerpetualNoteResponse;
  children: NoteNode[];
}

function buildTree(notes: PerpetualNoteResponse[]): NoteNode[] {
  const byParent = new Map<string | null, PerpetualNoteResponse[]>();
  notes.forEach((n) => {
    const k = n.parentPerpetualId;
    if (!byParent.has(k)) byParent.set(k, []);
    byParent.get(k)!.push(n);
  });
  byParent.forEach((arr) => arr.sort((a, b) => a.priority - b.priority));
  const build = (parentId: string | null): NoteNode[] =>
    (byParent.get(parentId) ?? []).map((n) => ({
      note: n,
      children: build(n.id),
    }));
  return build(null);
}

interface Props {
  boardId: string;
  selectedTabId: string | null;
  selectedNoteId: string | null;
  onSelectNote: (noteId: string) => void;
}

/**
 * 보드 좌측 영구노트 트리 패널 (P5) — 영구노트를 트리로 표시·선택·생성. 선택 시 우측
 * 인스펙터에서 *페이지 이동 없이* 편집. 영구노트는 탭에 속하므로 생성은 탭 선택을 요구.
 */
export function PerpetualPanel({
  boardId,
  selectedTabId,
  selectedNoteId,
  onSelectNote,
}: Props) {
  const notes = useBoardPerpetualNotes(boardId);
  const createNote = useCreatePerpetualNote(boardId);
  const dialogs = useDialogs();
  const tree = buildTree(notes.data ?? []);

  async function addNote() {
    if (!selectedTabId) {
      await dialogs.alert({
        title: "탭을 먼저 선택하세요",
        message:
          "영구노트는 탭에 속합니다. 좌측 탭 패널에서 탭을 선택한 뒤 추가하세요.",
      });
      return;
    }
    const title = await dialogs.prompt({
      title: "영구노트 추가",
      placeholder: "노트 제목",
    });
    if (title?.trim()) {
      createNote.mutate({ tabId: selectedTabId, title: title.trim() });
    }
  }

  function renderNodes(nodes: NoteNode[], depth: number) {
    return (
      <ul className="perpetual-panel-list">
        {nodes.map((node) => (
          <li key={node.note.id}>
            <button
              type="button"
              className={`perpetual-panel-item${selectedNoteId === node.note.id ? " selected" : ""}`}
              style={{ paddingLeft: `${0.5 + depth * 0.85}rem` }}
              onClick={() => onSelectNote(node.note.id)}
              title={node.note.title}
            >
              <span className="perpetual-panel-icon">◆</span>
              {node.note.title}
            </button>
            {node.children.length > 0 && renderNodes(node.children, depth + 1)}
          </li>
        ))}
      </ul>
    );
  }

  return (
    <section className="perpetual-panel">
      <header className="perpetual-panel-head">
        <span className="perpetual-panel-title">영구노트</span>
        <button
          type="button"
          className="perpetual-panel-add"
          onClick={addNote}
          title={selectedTabId ? "영구노트 추가" : "탭을 먼저 선택"}
        >
          + 노트
        </button>
      </header>
      <div className="perpetual-panel-scroll">
        {notes.isLoading ? (
          <p className="perpetual-panel-empty">불러오는 중...</p>
        ) : tree.length === 0 ? (
          <p className="perpetual-panel-empty">영구노트 없음</p>
        ) : (
          renderNodes(tree, 0)
        )}
      </div>
    </section>
  );
}
