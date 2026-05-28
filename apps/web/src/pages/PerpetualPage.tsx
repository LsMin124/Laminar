import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { MarkdownEditor } from "../components/editor/MarkdownEditor";
import {
  useBoard,
  useBoardPerpetualColumns,
  useBoardPerpetualNotes,
  useBoardTabs,
  useCommitVersion,
  useCreatePerpetualColumn,
  useCreatePerpetualNote,
  useCreateTab,
  useDeletePerpetualColumn,
  useDeletePerpetualNote,
  useDeleteTab,
  useMarkVersionCurrent,
  useNoteColumns,
  useNoteVersions,
  useUpdatePerpetualNote,
  useUpsertColumnValue,
} from "../lib/queries";
import type {
  PerpetualColumnType,
  PerpetualNoteResponse,
  TabResponse,
} from "../lib/types";
import "./PerpetualPage.css";

const COLUMN_TYPES: PerpetualColumnType[] = [
  "TEXT",
  "NUMBER",
  "DATE",
  "BOOLEAN",
  "ENUM",
  "JSON",
];

interface NoteTree {
  note: PerpetualNoteResponse;
  children: NoteTree[];
}

function buildNoteTree(notes: PerpetualNoteResponse[]): NoteTree[] {
  const byParent = new Map<string | null, PerpetualNoteResponse[]>();
  notes.forEach((n) => {
    const key = n.parentPerpetualId;
    const list = byParent.get(key) ?? [];
    list.push(n);
    byParent.set(key, list);
  });
  function build(parentId: string | null): NoteTree[] {
    const list = byParent.get(parentId) ?? [];
    return list
      .sort((a, b) => a.priority - b.priority)
      .map((n) => ({ note: n, children: build(n.id) }));
  }
  return build(null);
}

interface TabTree {
  tab: TabResponse;
  children: TabTree[];
}

function buildTabTree(tabs: TabResponse[]): TabTree[] {
  const byParent = new Map<string | null, TabResponse[]>();
  tabs.forEach((t) => {
    const key = t.parentTabId;
    const list = byParent.get(key) ?? [];
    list.push(t);
    byParent.set(key, list);
  });
  function build(parentId: string | null): TabTree[] {
    const list = byParent.get(parentId) ?? [];
    return list
      .sort((a, b) => a.priority - b.priority)
      .map((t) => ({ tab: t, children: build(t.id) }));
  }
  return build(null);
}

export function PerpetualPage() {
  const params = useParams();
  const navigate = useNavigate();
  const boardId = params.boardId ?? "";

  const board = useBoard(boardId);
  const tabs = useBoardTabs(boardId);
  const notes = useBoardPerpetualNotes(boardId);
  const columns = useBoardPerpetualColumns(boardId);

  const [selectedTabId, setSelectedTabId] = useState<string | null>(null);
  const [selectedNoteId, setSelectedNoteId] = useState<string | null>(null);

  const createTab = useCreateTab(boardId);
  const deleteTab = useDeleteTab(boardId);
  const createNote = useCreatePerpetualNote(boardId);
  const deleteNote = useDeletePerpetualNote(boardId);
  const createColumn = useCreatePerpetualColumn(boardId);
  const deleteColumn = useDeletePerpetualColumn(boardId);

  const tabTree = useMemo(() => buildTabTree(tabs.data ?? []), [tabs.data]);
  const notesInTab = useMemo(() => {
    if (!selectedTabId) return [];
    return (notes.data ?? []).filter((n) => n.tabId === selectedTabId);
  }, [notes.data, selectedTabId]);
  const noteTree = useMemo(() => buildNoteTree(notesInTab), [notesInTab]);

  return (
    <div className="perpetual-page">
      <header className="perpetual-header">
        <button
          type="button"
          className="board-detail-back"
          onClick={() => navigate(`/boards/${boardId}`)}
        >
          ← 보드
        </button>
        <h1>영구노트</h1>
        {board.data && (
          <span className="board-detail-slug">/{board.data.slug}</span>
        )}
      </header>

      <div className="perpetual-grid">
        <aside className="perpetual-tabs">
          <header className="perpetual-tabs-head">
            <h2>탭</h2>
            <button
              type="button"
              onClick={() => {
                const name = prompt("탭 이름");
                if (name) createTab.mutate({ name });
              }}
            >
              +
            </button>
          </header>
          <TabTreeList
            tree={tabTree}
            depth={0}
            selectedId={selectedTabId}
            onSelect={(id) => {
              setSelectedTabId(id);
              setSelectedNoteId(null);
            }}
            onDelete={(id) => {
              if (confirm("탭을 삭제할까요? (하위 노트 분리)")) {
                deleteTab.mutate(id);
                if (selectedTabId === id) setSelectedTabId(null);
              }
            }}
            onCreateChild={(parentId) => {
              const name = prompt("하위 탭 이름");
              if (name) createTab.mutate({ name, parentTabId: parentId });
            }}
          />
        </aside>

        <section className="perpetual-notes">
          <header className="perpetual-notes-head">
            <h2>노트</h2>
            <button
              type="button"
              disabled={!selectedTabId}
              onClick={() => {
                if (!selectedTabId) return;
                const title = prompt("노트 제목");
                if (title) createNote.mutate({ tabId: selectedTabId, title });
              }}
            >
              + 노트
            </button>
          </header>
          {!selectedTabId ? (
            <p className="markdown-empty">탭을 먼저 선택하세요.</p>
          ) : (
            <NoteTreeList
              tree={noteTree}
              depth={0}
              selectedId={selectedNoteId}
              onSelect={setSelectedNoteId}
              onCreateChild={(parentId) => {
                const title = prompt("하위 노트 제목");
                if (title)
                  createNote.mutate({
                    tabId: selectedTabId,
                    parentPerpetualId: parentId,
                    title,
                  });
              }}
              onDelete={(id) => {
                if (confirm("노트를 삭제할까요?")) {
                  deleteNote.mutate(id);
                  if (selectedNoteId === id) setSelectedNoteId(null);
                }
              }}
            />
          )}
        </section>

        <main className="perpetual-detail">
          {selectedNoteId ? (
            <NoteDetail
              boardId={boardId}
              noteId={selectedNoteId}
              notes={notes.data ?? []}
            />
          ) : (
            <p className="markdown-empty">노트를 선택하세요.</p>
          )}
        </main>
      </div>

      <section className="perpetual-columns">
        <header className="perpetual-columns-head">
          <h2>시트 컬럼 정의 ({columns.data?.length ?? 0})</h2>
          <button
            type="button"
            onClick={() => {
              const name = prompt("컬럼 이름");
              if (!name) return;
              const type = prompt(
                "타입: " + COLUMN_TYPES.join("/"),
                "TEXT",
              ) as PerpetualColumnType;
              if (!COLUMN_TYPES.includes(type)) return;
              let enumValues: string[] | undefined;
              if (type === "ENUM") {
                const raw = prompt("ENUM 값 (콤마 구분)") ?? "";
                enumValues = raw
                  .split(",")
                  .map((v) => v.trim())
                  .filter(Boolean);
              }
              createColumn.mutate({ name, type, enumValues });
            }}
          >
            + 컬럼
          </button>
        </header>
        <ul className="perpetual-column-list">
          {columns.data?.map((c) => (
            <li key={c.id} className="perpetual-column-item">
              <span className="perpetual-column-name">{c.name}</span>
              <span className="perpetual-column-type">{c.type}</span>
              {c.type === "ENUM" && c.enumValues && (
                <span className="perpetual-column-enum">
                  {c.enumValues.join(" · ")}
                </span>
              )}
              <button
                type="button"
                className="members-item-remove"
                onClick={() => {
                  if (confirm(`'${c.name}' 컬럼 삭제?`)) {
                    deleteColumn.mutate(c.id);
                  }
                }}
              >
                삭제
              </button>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

interface TabTreeListProps {
  tree: TabTree[];
  depth: number;
  selectedId: string | null;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
  onCreateChild: (parentId: string) => void;
}

function TabTreeList({
  tree,
  depth,
  selectedId,
  onSelect,
  onDelete,
  onCreateChild,
}: TabTreeListProps) {
  return (
    <ul className="perpetual-tree">
      {tree.map((node) => (
        <li key={node.tab.id}>
          <div
            className={`perpetual-tree-item${selectedId === node.tab.id ? " selected" : ""}`}
            style={{ paddingLeft: `${depth * 0.8 + 0.4}rem` }}
          >
            <button
              type="button"
              className="perpetual-tree-label"
              onClick={() => onSelect(node.tab.id)}
            >
              {node.tab.name}
            </button>
            <button
              type="button"
              className="perpetual-tree-action"
              onClick={() => onCreateChild(node.tab.id)}
              title="하위 탭 추가"
            >
              +
            </button>
            {depth < 9 && (
              <button
                type="button"
                className="perpetual-tree-action danger"
                onClick={() => onDelete(node.tab.id)}
                title="삭제"
              >
                ×
              </button>
            )}
          </div>
          {node.children.length > 0 && (
            <TabTreeList
              tree={node.children}
              depth={depth + 1}
              selectedId={selectedId}
              onSelect={onSelect}
              onDelete={onDelete}
              onCreateChild={onCreateChild}
            />
          )}
        </li>
      ))}
    </ul>
  );
}

interface NoteTreeListProps {
  tree: NoteTree[];
  depth: number;
  selectedId: string | null;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
  onCreateChild: (parentId: string) => void;
}

function NoteTreeList({
  tree,
  depth,
  selectedId,
  onSelect,
  onDelete,
  onCreateChild,
}: NoteTreeListProps) {
  return (
    <ul className="perpetual-tree">
      {tree.map((node) => (
        <li key={node.note.id}>
          <div
            className={`perpetual-tree-item${selectedId === node.note.id ? " selected" : ""}`}
            style={{ paddingLeft: `${depth * 0.8 + 0.4}rem` }}
          >
            <button
              type="button"
              className="perpetual-tree-label"
              onClick={() => onSelect(node.note.id)}
            >
              {node.note.title}
            </button>
            {depth < 9 && (
              <button
                type="button"
                className="perpetual-tree-action"
                onClick={() => onCreateChild(node.note.id)}
                title="하위 노트 추가"
              >
                +
              </button>
            )}
            <button
              type="button"
              className="perpetual-tree-action danger"
              onClick={() => onDelete(node.note.id)}
              title="삭제"
            >
              ×
            </button>
          </div>
          {node.children.length > 0 && (
            <NoteTreeList
              tree={node.children}
              depth={depth + 1}
              selectedId={selectedId}
              onSelect={onSelect}
              onDelete={onDelete}
              onCreateChild={onCreateChild}
            />
          )}
        </li>
      ))}
    </ul>
  );
}

interface NoteDetailProps {
  boardId: string;
  noteId: string;
  notes: PerpetualNoteResponse[];
}

function NoteDetail({ boardId, noteId, notes }: NoteDetailProps) {
  const note = notes.find((n) => n.id === noteId);
  const columns = useBoardPerpetualColumns(boardId);
  const noteColumns = useNoteColumns(noteId);
  const versions = useNoteVersions(noteId);
  const updateNote = useUpdatePerpetualNote(boardId, noteId);
  const upsertValue = useUpsertColumnValue(noteId);
  const commitVersion = useCommitVersion(noteId);
  const markCurrent = useMarkVersionCurrent(noteId);

  const [title, setTitle] = useState(note?.title ?? "");
  const [bodyMd, setBodyMd] = useState(note?.bodyMd ?? "");
  const [draftCommitSummary, setDraftCommitSummary] = useState("");
  const [draftDiff, setDraftDiff] = useState("");

  if (!note) {
    return <p className="markdown-empty">노트를 찾을 수 없습니다.</p>;
  }

  const valueByColumnId = new Map(
    (noteColumns.data ?? []).map((v) => [v.columnDefinitionId, v.value]),
  );

  return (
    <div className="note-detail">
      <header className="note-detail-head">
        <input
          className="note-detail-title-input"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          onBlur={() => {
            if (title !== note.title) {
              updateNote.mutate({ title });
            }
          }}
          maxLength={200}
        />
      </header>

      <section className="note-detail-section">
        <h3>본문</h3>
        <MarkdownEditor
          value={bodyMd}
          onChange={setBodyMd}
          minHeight={200}
        />
        <div className="note-detail-actions">
          <button
            type="button"
            className="primary"
            onClick={() => {
              if (bodyMd !== note.bodyMd) {
                updateNote.mutate({ bodyMd });
              }
            }}
          >
            본문 저장
          </button>
        </div>
      </section>

      <section className="note-detail-section">
        <h3>시트 컬럼</h3>
        {(columns.data ?? []).length === 0 ? (
          <p className="markdown-empty">
            컬럼 정의가 없습니다. 아래에서 추가하세요.
          </p>
        ) : (
          <ul className="note-detail-values">
            {columns.data?.map((col) => (
              <li key={col.id} className="note-detail-value">
                <span className="note-detail-value-name">
                  {col.name}
                  <span className="note-detail-value-type">{col.type}</span>
                </span>
                <ColumnValueInput
                  type={col.type}
                  enumValues={col.enumValues}
                  value={valueByColumnId.get(col.id) ?? null}
                  onCommit={(v) =>
                    upsertValue.mutate({
                      columnDefinitionId: col.id,
                      value: v,
                    })
                  }
                />
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="note-detail-section">
        <h3>버전 ({versions.data?.length ?? 0})</h3>
        <div className="note-detail-commit">
          <input
            type="text"
            placeholder="버전 요약 (선택)"
            value={draftCommitSummary}
            onChange={(e) => setDraftCommitSummary(e.target.value)}
            maxLength={500}
          />
          <textarea
            placeholder="diff (Markdown · 선택)"
            value={draftDiff}
            onChange={(e) => setDraftDiff(e.target.value)}
            rows={3}
          />
          <button
            type="button"
            className="primary"
            onClick={() => {
              commitVersion.mutate({
                summary: draftCommitSummary || undefined,
                bodyDiffMd: draftDiff || undefined,
                markCurrent: true,
              });
              setDraftCommitSummary("");
              setDraftDiff("");
            }}
          >
            커밋
          </button>
        </div>
        <ul className="note-detail-versions">
          {versions.data
            ?.slice()
            .sort((a, b) => b.versionNumber - a.versionNumber)
            .map((v) => (
              <li
                key={v.id}
                className={`note-detail-version${v.currentDiff ? " current" : ""}`}
              >
                <div className="version-line">
                  <span className="version-num">v{v.versionNumber}</span>
                  <span className="version-summary">
                    {v.summary ?? "(요약 없음)"}
                  </span>
                  {v.currentDiff && (
                    <span className="version-badge">CURRENT</span>
                  )}
                  {!v.currentDiff && (
                    <button
                      type="button"
                      onClick={() => markCurrent.mutate(v.id)}
                    >
                      현재로 표시
                    </button>
                  )}
                </div>
                {v.bodyDiffMd && (
                  <pre className="version-diff">{v.bodyDiffMd}</pre>
                )}
                <time className="version-time">
                  {new Date(v.committedAt).toLocaleString()}
                </time>
              </li>
            ))}
        </ul>
      </section>
    </div>
  );
}

interface ColumnValueInputProps {
  type: PerpetualColumnType;
  enumValues: string[] | null;
  value: string | null;
  onCommit: (value: string | null) => void;
}

function ColumnValueInput({
  type,
  enumValues,
  value,
  onCommit,
}: ColumnValueInputProps) {
  const [draft, setDraft] = useState(value ?? "");
  function commit() {
    if (draft !== (value ?? "")) {
      onCommit(draft || null);
    }
  }
  if (type === "BOOLEAN") {
    return (
      <select
        value={value ?? ""}
        onChange={(e) => onCommit(e.target.value || null)}
      >
        <option value="">—</option>
        <option value="true">true</option>
        <option value="false">false</option>
      </select>
    );
  }
  if (type === "ENUM" && enumValues) {
    return (
      <select
        value={value ?? ""}
        onChange={(e) => onCommit(e.target.value || null)}
      >
        <option value="">—</option>
        {enumValues.map((v) => (
          <option key={v} value={v}>
            {v}
          </option>
        ))}
      </select>
    );
  }
  return (
    <input
      type={
        type === "NUMBER" ? "number" : type === "DATE" ? "date" : "text"
      }
      value={draft}
      onChange={(e) => setDraft(e.target.value)}
      onBlur={commit}
      onKeyDown={(e) => {
        if (e.key === "Enter") (e.target as HTMLInputElement).blur();
      }}
    />
  );
}
