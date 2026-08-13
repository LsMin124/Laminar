import { memo, useMemo, useState } from "react";
import { MarkdownView } from "../doc/MarkdownDoc";
import { useAttachmentInlineUrl } from "../../lib/attachments";
import type { WhiteboardNode as WbNode } from "../../lib/whiteboard";
import { NEW_NODE_H, NEW_NODE_W } from "./whiteboardGeometry";
import { COLORABLE_KINDS, paletteEntry, shapeOf } from "./whiteboardPalette";

/** 이미지 노드의 첨부 id — attrs.attachmentId(문자열)만 채택, 없으면 null(업로드 중). */
function imageAttachmentId(node: WbNode): string | null {
  const id = node.attrs?.attachmentId;
  return typeof id === "string" ? id : null;
}

type EditorMode = "md" | "body" | "label";

/** kind별 인라인 편집 형태 — md=제목+본문, 스티키·텍스트=본문만, 도형·섹션=라벨만. */
function editorModeOf(kind: WbNode["kind"]): EditorMode {
  if (kind === "SHAPE" || kind === "SECTION") return "label";
  if (kind === "STICKY" || kind === "TEXT") return "body";
  return "md";
}

function emptyHintOf(kind: WbNode["kind"]): string {
  if (kind === "STICKY") return "빈 스티키 — 더블클릭해 입력";
  if (kind === "TEXT") return "텍스트 — 더블클릭해 입력";
  return "빈 노드 — 더블클릭해 편집";
}

/**
 * 화이트보드 노드 — 자유 배치 md·이미지·스티키·도형·텍스트 카드. 좌표·상태·드래그/연결/리사이즈
 * 핸들러는 컨테이너(WhiteboardCanvas)가 주입한다. 더블클릭 시 인라인 편집(kind별 형태).
 * 색·도형 종류는 attrs(color·shape id) — CSS 변수 --wb-fill/--wb-ink로 전달.
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
  const isShape = node.kind === "SHAPE";
  const isPen = node.kind === "PEN";
  const isSection = node.kind === "SECTION";
  // 이미지는 본문 편집이 없고 펜은 편집 대상 자체가 없다.
  const editable = !isImage && !isPen;
  const pal = COLORABLE_KINDS.has(node.kind) ? paletteEntry(node.attrs.color, node.kind) : null;
  // 마크다운 파싱은 본문이 바뀔 때만 — 이 노드 자신의 드래그 프레임에서도 캐시가 유지된다.
  const mdBody = useMemo(
    () => (node.bodyMd ? <MarkdownView source={node.bodyMd} /> : null),
    [node.bodyMd],
  );

  const style = {
    left: node.x,
    top: node.y,
    width: w,
    height: h,
    ...(pal ? { "--wb-fill": pal.fill, "--wb-ink": pal.ink } : {}),
  } as React.CSSProperties;

  return (
    <div
      className={`wb-node ${node.kind.toLowerCase()}${selected ? " selected" : ""}${
        editing ? " editing" : ""
      }`}
      style={style}
      onPointerDown={(e) => {
        if (!editing) onBodyDown(e, node);
      }}
      onClick={(e) => e.stopPropagation()}
      onDoubleClick={(e) => {
        e.stopPropagation();
        if (editable) onStartEdit(node.id);
      }}
    >
      {editing && editable ? (
        <NodeEditor
          node={node}
          mode={editorModeOf(node.kind)}
          onSave={onSaveEdit}
          onCancel={onCancelEdit}
        />
      ) : isPen ? (
        <PenBody node={node} stroke={pal?.fill ?? "var(--accent)"} />
      ) : isSection ? (
        <div className="wb-section-title">{node.text || "섹션"}</div>
      ) : isShape ? (
        <>
          <div className={`wb-shape ${shapeOf(node.attrs)}`} />
          {node.text && <div className="wb-shape-label">{node.text}</div>}
        </>
      ) : (
        <>
          {(node.kind === "MD" || isImage) && node.text && (
            <div className="wb-node-title">{node.text}</div>
          )}
          <div className="wb-node-body">
            {isImage ? (
              <ImageNodeBody attachmentId={imageAttachmentId(node)} />
            ) : (
              (mdBody ?? <span className="wb-node-empty">{emptyHintOf(node.kind)}</span>)
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

/** attrs.points(평탄 [x0,y0,...]) → SVG polyline points 문자열. 숫자 배열이 아니면 빈 문자열. */
function penPointsOf(attrs: Record<string, unknown>): string {
  const raw = attrs.points;
  if (!Array.isArray(raw)) return "";
  const parts: string[] = [];
  for (let i = 0; i + 1 < raw.length; i += 2) {
    const x = raw[i];
    const y = raw[i + 1];
    if (typeof x !== "number" || typeof y !== "number") return "";
    parts.push(`${x},${y}`);
  }
  return parts.join(" ");
}

/** 펜 스트로크 — 저장 당시 bbox(attrs.ow/oh)를 viewBox로 그려 리사이즈 시 스트로크가 함께 스케일된다. */
function PenBody({ node, stroke }: { node: WbNode; stroke: string }) {
  const ow =
    typeof node.attrs.ow === "number" && node.attrs.ow > 0 ? node.attrs.ow : (node.width ?? NEW_NODE_W);
  const oh =
    typeof node.attrs.oh === "number" && node.attrs.oh > 0
      ? node.attrs.oh
      : (node.height ?? NEW_NODE_H);
  return (
    <svg className="wb-pen" viewBox={`0 0 ${ow} ${oh}`} preserveAspectRatio="none" aria-hidden="true">
      <polyline points={penPointsOf(node.attrs)} style={{ stroke }} />
    </svg>
  );
}

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
 * 되고 별도 sync 효과가 필요 없다(마운트=편집 시작). mode에 따라 제목/본문 필드를 가감한다.
 */
function NodeEditor({
  node,
  mode,
  onSave,
  onCancel,
}: {
  node: WbNode;
  mode: EditorMode;
  onSave: (id: string, patch: { text: string; bodyMd: string }) => void;
  onCancel: () => void;
}) {
  const [text, setText] = useState(node.text ?? "");
  const [body, setBody] = useState(node.bodyMd ?? "");
  return (
    <div className="wb-node-edit" onPointerDown={(e) => e.stopPropagation()}>
      {mode !== "body" && (
        <input
          className="wb-node-title-input"
          value={text}
          placeholder={mode === "label" ? "라벨" : "제목"}
          onChange={(e) => setText(e.target.value)}
          autoFocus
        />
      )}
      {mode !== "label" && (
        <textarea
          className="wb-node-body-input"
          value={body}
          placeholder={mode === "body" ? "내용…" : "마크다운 본문…"}
          onChange={(e) => setBody(e.target.value)}
          autoFocus={mode === "body"}
        />
      )}
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
