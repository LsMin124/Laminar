import { useState } from "react";
import {
  useBoardPerpetualNotes,
  useCommitVersion,
  useMarkVersionCurrent,
  useNoteVersions,
  useUpdatePerpetualNote,
} from "../../lib/queries";
import "./PerpetualNoteInspector.css";

interface Props {
  boardId: string;
  noteId: string;
  onClose: () => void;
}

/**
 * 영구노트 인스펙터 (P5) — 보드 작업면 우측 패널에서 영구노트를 *페이지 이동 없이* 보고 편집.
 * 본문 편집 + perpetual-ver 버전 이력(커밋·현재 diff 지정). 시트 컬럼 등 상세는 영구노트 페이지에.
 */
export function PerpetualNoteInspector({ boardId, noteId, onClose }: Props) {
  const notes = useBoardPerpetualNotes(boardId);
  const versions = useNoteVersions(noteId);
  const updateNote = useUpdatePerpetualNote(boardId, noteId);
  const commitVersion = useCommitVersion(noteId);
  const markCurrent = useMarkVersionCurrent(noteId);

  const note = notes.data?.find((n) => n.id === noteId);
  const [title, setTitle] = useState(note?.title ?? "");
  const [bodyMd, setBodyMd] = useState(note?.bodyMd ?? "");
  const [summary, setSummary] = useState("");

  return (
    <aside className="card-inspector perpetual-inspector">
      <header className="card-inspector-head">
        <span className="card-inspector-label">영구노트</span>
        <button
          type="button"
          className="card-inspector-close"
          onClick={onClose}
          aria-label="인스펙터 닫기"
        >
          ✕
        </button>
      </header>

      {notes.isLoading ? (
        <p className="loading">불러오는 중...</p>
      ) : !note ? (
        <p className="auth-error">노트를 찾을 수 없습니다.</p>
      ) : (
        <div className="card-inspector-body">
          <input
            className="perpetual-title-input"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            onBlur={() => {
              if (title !== note.title) updateNote.mutate({ title });
            }}
            maxLength={200}
          />

          <label className="perpetual-field">
            본문
            <textarea
              value={bodyMd}
              onChange={(e) => setBodyMd(e.target.value)}
              rows={10}
            />
          </label>
          <button
            type="button"
            className="perpetual-save"
            disabled={bodyMd === note.bodyMd || updateNote.isPending}
            onClick={() => updateNote.mutate({ bodyMd })}
          >
            본문 저장
          </button>

          <section className="perpetual-versions">
            <h3>버전 ({versions.data?.length ?? 0})</h3>
            <div className="perpetual-commit">
              <input
                type="text"
                placeholder="버전 요약 (선택)"
                value={summary}
                onChange={(e) => setSummary(e.target.value)}
                maxLength={500}
              />
              <button
                type="button"
                onClick={() => {
                  commitVersion.mutate({ summary: summary || undefined });
                  setSummary("");
                }}
                disabled={commitVersion.isPending}
              >
                버전 커밋
              </button>
            </div>
            <ul className="perpetual-version-list">
              {(versions.data ?? [])
                .slice()
                .sort((a, b) => b.versionNumber - a.versionNumber)
                .map((v) => (
                  <li
                    key={v.id}
                    className={`perpetual-version${v.currentDiff ? " current" : ""}`}
                  >
                    <span className="perpetual-version-num">
                      v{v.versionNumber}
                    </span>
                    <span className="perpetual-version-summary">
                      {v.summary ?? "—"}
                    </span>
                    {v.currentDiff ? (
                      <span className="perpetual-version-badge">현재</span>
                    ) : (
                      <button
                        type="button"
                        className="perpetual-version-mark"
                        onClick={() => markCurrent.mutate(v.id)}
                      >
                        현재로
                      </button>
                    )}
                  </li>
                ))}
            </ul>
          </section>
        </div>
      )}
    </aside>
  );
}
