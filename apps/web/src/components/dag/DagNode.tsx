import { BAR_H, cardMeta } from "./dagGeometry";
import { CardCategoryTag } from "./CardCategoryTag";
import type { Card, Category } from "../../lib/dag";

interface NodeGeom {
  x: number;
  y: number;
  w: number;
}

/**
 * 카드 노드 막대 — 헤더(체크·제목·분류태그) / 본문 발췌 2줄 / 푸터(날짜·관계·지연 인디케이터).
 * 좌우 끝 리사이즈 핸들 + 우측 연결 nub. 좌표·상태·핸들러는 컨테이너(DagCanvas)에서 주입.
 */
export function DagNode({
  card,
  geom,
  selected,
  isLinkSource,
  overdue,
  excerpt,
  rels,
  categoryId,
  tabId,
  categories,
  onBodyDown,
  onHandleDown,
  onPointerMove,
  onPointerUp,
  onOpenCard,
  onToggleComplete,
  onNubDown,
  onNubMove,
  onNubUp,
}: {
  card: Card;
  geom: NodeGeom;
  selected: boolean;
  isLinkSource: boolean;
  overdue: boolean;
  excerpt: string | undefined;
  rels: number;
  categoryId: string | null;
  tabId: string;
  categories: Category[];
  onBodyDown: (e: React.PointerEvent<HTMLDivElement>, c: Card) => void;
  onHandleDown: (e: React.PointerEvent<HTMLDivElement>, c: Card, mode: "resize-l" | "resize-r") => void;
  onPointerMove: (e: React.PointerEvent<HTMLDivElement>) => void;
  onPointerUp: (c: Card) => void;
  onOpenCard?: (cardId: string, title: string) => void;
  onToggleComplete: (cardId: string, completed: boolean) => void;
  onNubDown: (e: React.PointerEvent<HTMLSpanElement>, c: Card) => void;
  onNubMove: (e: React.PointerEvent<HTMLSpanElement>) => void;
  onNubUp: (e: React.PointerEvent<HTMLSpanElement>) => void;
}) {
  const c = card;
  const g = geom;
  const dated = !!c.startDate;
  return (
    <div
      className={`dag-node${c.completed ? " completed" : ""}${
        isLinkSource ? " link-src" : ""
      }${selected ? " selected" : ""}${overdue ? " overdue" : ""}`}
      style={{ left: g.x, top: g.y, width: g.w, height: BAR_H }}
      onPointerDown={(e) => onBodyDown(e, c)}
      onPointerMove={onPointerMove}
      onPointerUp={() => onPointerUp(c)}
      onDoubleClick={(e) => {
        e.stopPropagation();
        onOpenCard?.(c.id, c.title);
      }}
    >
      <span className="dag-node-stripe" />
      {dated && (
        <div
          className="dag-handle l"
          onPointerDown={(e) => onHandleDown(e, c, "resize-l")}
          onPointerMove={onPointerMove}
          onPointerUp={() => onPointerUp(c)}
        />
      )}
      <div className="dag-node-main">
        <div className="dag-node-head">
          <input
            type="checkbox"
            className="dag-node-check"
            checked={c.completed}
            onPointerDown={(e) => e.stopPropagation()}
            onClick={(e) => e.stopPropagation()}
            onChange={(e) => onToggleComplete(c.id, e.target.checked)}
            title="완료 여부"
          />
          <div className="dag-node-title">{c.title || "(제목 없음)"}</div>
          <CardCategoryTag
            tabId={tabId}
            cardId={c.id}
            categoryId={categoryId}
            categories={categories}
          />
        </div>
        <div className="dag-node-excerpt">
          {excerpt ?? <span className="dag-node-empty">빈 문서</span>}
        </div>
        <div className="dag-node-foot">
          <span className="dag-node-date">{cardMeta(c)}</span>
          <div className="dag-node-ind">
            {overdue && (
              <span className="dag-ind danger" title="지연(종료일 경과)">
                ●
              </span>
            )}
            {rels > 0 && (
              <span className="dag-ind" title={`관계 ${rels}개`}>
                ↔{rels}
              </span>
            )}
          </div>
        </div>
      </div>
      {dated && (
        <div
          className="dag-handle r"
          onPointerDown={(e) => onHandleDown(e, c, "resize-r")}
          onPointerMove={onPointerMove}
          onPointerUp={() => onPointerUp(c)}
        />
      )}
      <span
        className="dag-link-nub"
        title="드래그해 다음 카드로 연결"
        onPointerDown={(e) => onNubDown(e, c)}
        onPointerMove={onNubMove}
        onPointerUp={onNubUp}
      />
    </div>
  );
}
