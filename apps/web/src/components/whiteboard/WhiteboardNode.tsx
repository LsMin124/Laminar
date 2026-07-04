import { useState } from "react";
import { MarkdownView } from "../doc/MarkdownDoc";
import type { WhiteboardNode as WbNode } from "../../lib/whiteboard";
import { NEW_NODE_H, NEW_NODE_W } from "./whiteboardGeometry";

/**
 * 화이트보드 노드 — 자유 배치 md·이미지 카드. 좌표·상태·드래그/연결/리사이즈 핸들러는
 * 컨테이너(WhiteboardCanvas)가 주입한다. 더블클릭 시 인라인 편집(제목 + 마크다운 본문).
 */
export function WhiteboardNode({
  node,
  selected,
  editing,
  onBodyDown,
  onNubDown,
  onResizeDown,
  onSelect,
  onStartEdit,
  onSaveEdit,
  onCancelEdit,
  onDelete,
}: {
  node: WbNode;
  selected: boolean;
  editing: boolean;
  onBodyDown: (e: React.PointerEvent<HTMLDivElement>, node: WbNode) => void;
  onNubDown: (e: React.PointerEvent<HTMLSpanElement>, node: WbNode) => void;
  onResizeDown: (e: React.PointerEvent<HTMLSpanElement>, node: WbNode) => void;
  onSelect: (id: string) => void;
  onStartEdit: (id: string) => void;
  onSaveEdit: (id: string, patch: { text: string; bodyMd: string }) => void;
  onCancelEdit: () => void;
  onDelete: (id: string) => void;
}) {
  const w = node.width ?? NEW_NODE_W;
  const h = node.height ?? NEW_NODE_H;

  return (
    <div
      className={`wb-node ${node.kind.toLowerCase()}${selected ? " selected" : ""}${
        editing ? " editing" : ""
      }`}
      style={{ left: node.x, top: node.y, width: w, height: h }}
      onPointerDown={(e) => {
        if (!editing) onBodyDown(e, node);
      }}
      onClick={(e) => {
        e.stopPropagation();
        onSelect(node.id);
      }}
      onDoubleClick={(e) => {
        e.stopPropagation();
        onStartEdit(node.id);
      }}
    >
      {editing ? (
        <NodeEditor node={node} onSave={onSaveEdit} onCancel={onCancelEdit} />
      ) : (
        <>
          {node.text && <div className="wb-node-title">{node.text}</div>}
          <div className="wb-node-body">
            {node.kind === "IMAGE" ? (
              <div className="wb-node-image-ph">이미지 노드 (Phase 3)</div>
            ) : node.bodyMd ? (
              <MarkdownView source={node.bodyMd} />
            ) : (
              <span className="wb-node-empty">빈 노드 — 더블클릭해 편집</span>
            )}
          </div>
        </>
      )}
      {selected && !editing && (
        <button
          type="button"
          className="wb-node-del"
          title="삭제"
          onPointerDown={(e) => e.stopPropagation()}
          onClick={(e) => {
            e.stopPropagation();
            onDelete(node.id);
          }}
        >
          ✕
        </button>
      )}
      <span
        className="wb-node-nub"
        title="드래그해 다른 노드로 연결"
        onPointerDown={(e) => onNubDown(e, node)}
      />
      {selected && !editing && (
        <span className="wb-node-resize" onPointerDown={(e) => onResizeDown(e, node)} />
      )}
    </div>
  );
}

/**
 * 인라인 편집 폼 — 편집 진입 시에만 마운트되므로 초기 드래프트를 node 값으로 useState 초기화하면
 * 되고 별도 sync 효과가 필요 없다(마운트=편집 시작).
 */
function NodeEditor({
  node,
  onSave,
  onCancel,
}: {
  node: WbNode;
  onSave: (id: string, patch: { text: string; bodyMd: string }) => void;
  onCancel: () => void;
}) {
  const [text, setText] = useState(node.text ?? "");
  const [body, setBody] = useState(node.bodyMd ?? "");
  return (
    <div className="wb-node-edit" onPointerDown={(e) => e.stopPropagation()}>
      <input
        className="wb-node-title-input"
        value={text}
        placeholder="제목"
        onChange={(e) => setText(e.target.value)}
        autoFocus
      />
      <textarea
        className="wb-node-body-input"
        value={body}
        placeholder="마크다운 본문…"
        onChange={(e) => setBody(e.target.value)}
      />
      <div className="wb-node-edit-actions">
        <button type="button" onClick={onCancel}>
          취소
        </button>
        <button type="button" className="primary" onClick={() => onSave(node.id, { text, bodyMd: body })}>
          저장
        </button>
      </div>
    </div>
  );
}
