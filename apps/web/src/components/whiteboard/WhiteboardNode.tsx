import { memo, useMemo, useState } from "react";
import { MarkdownView } from "../doc/MarkdownDoc";
import { useAttachmentInlineUrl } from "../../lib/attachments";
import type { WhiteboardNode as WbNode } from "../../lib/whiteboard";
import { NEW_NODE_H, NEW_NODE_W } from "./whiteboardGeometry";

/** 이미지 노드의 첨부 id — attrs.attachmentId(문자열)만 채택, 없으면 null(업로드 중). */
function imageAttachmentId(node: WbNode): string | null {
  const id = node.attrs?.attachmentId;
  return typeof id === "string" ? id : null;
}

/**
 * 화이트보드 노드 — 자유 배치 md·이미지 카드. 좌표·상태·드래그/연결/리사이즈 핸들러는
 * 컨테이너(WhiteboardCanvas)가 주입한다. 더블클릭 시 인라인 편집(제목 + 마크다운 본문).
 * memo — 팬/줌/마퀴/다른 노드 드래그 프레임에서 무변경 노드의 재렌더(마크다운 재파싱)를 건너뛴다.
 * 컨테이너는 핸들러 항등성(useStableHandler)과 무변경 노드의 객체 항등성을 보장한다.
 */
export const WhiteboardNode = memo(function WhiteboardNode({
  node,
  selected,
  editing,
  onBodyDown,
  onNubDown,
  onResizeDown,
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
  onStartEdit: (id: string) => void;
  onSaveEdit: (id: string, patch: { text: string; bodyMd: string }) => void;
  onCancelEdit: () => void;
  onDelete: (id: string) => void;
}) {
  const w = node.width ?? NEW_NODE_W;
  const h = node.height ?? NEW_NODE_H;
  const isImage = node.kind === "IMAGE";
  // 마크다운 파싱은 본문이 바뀔 때만 — 이 노드 자신의 드래그 프레임에서도 캐시가 유지된다.
  const mdBody = useMemo(
    () => (node.bodyMd ? <MarkdownView source={node.bodyMd} /> : null),
    [node.bodyMd],
  );

  return (
    <div
      className={`wb-node ${node.kind.toLowerCase()}${selected ? " selected" : ""}${
        editing ? " editing" : ""
      }`}
      style={{ left: node.x, top: node.y, width: w, height: h }}
      onPointerDown={(e) => {
        if (!editing) onBodyDown(e, node);
      }}
      onClick={(e) => e.stopPropagation()}
      onDoubleClick={(e) => {
        e.stopPropagation();
        // 이미지 노드는 본문 편집이 없으므로 더블클릭 편집 진입 안 함.
        if (!isImage) onStartEdit(node.id);
      }}
    >
      {editing && !isImage ? (
        <NodeEditor node={node} onSave={onSaveEdit} onCancel={onCancelEdit} />
      ) : (
        <>
          {node.text && <div className="wb-node-title">{node.text}</div>}
          <div className="wb-node-body">
            {isImage ? (
              <ImageNodeBody attachmentId={imageAttachmentId(node)} />
            ) : (
              (mdBody ?? <span className="wb-node-empty">빈 노드 — 더블클릭해 편집</span>)
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
});

/** 이미지 노드 본문 — 인라인 presigned URL(5분 TTL, 자동 갱신)로 {@code <img>} 렌더. */
function ImageNodeBody({ attachmentId }: { attachmentId: string | null }) {
  const { data: url, isLoading, isError } = useAttachmentInlineUrl(attachmentId);
  if (!attachmentId) return <div className="wb-node-image-ph">이미지 업로드 중…</div>;
  if (isError) return <div className="wb-node-image-ph">이미지를 불러오지 못함</div>;
  if (isLoading || !url) return <div className="wb-node-image-ph">이미지 로딩…</div>;
  return <img className="wb-node-img" src={url} alt="" draggable={false} />;
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
