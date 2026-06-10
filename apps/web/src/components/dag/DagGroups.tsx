import type { Group } from "../../lib/graphTypes";

/** 그룹 경계 박스 geometry(멤버 카드 bounding rect) — DagGroups 박스 렌더와 DagEdges 그룹 화살표 앵커가 공유. */
export interface GroupRect {
  x: number;
  y: number;
  w: number;
  h: number;
}

/**
 * 그룹 경계 박스 레이어 — 멤버 카드 bounding rect 위에 점선 박스 + 라벨(이름 + ▤본문/⇢연결/✕삭제 버튼).
 * 박스는 pointer-events:none(투과), 라벨만 활성. geometry·핸들러는 컨테이너에서 주입.
 */
export function DagGroups({
  groups,
  groupRects,
  groupLinkSource,
  onOpenGroup,
  onGroupLinkBtn,
  onDeleteGroup,
}: {
  groups: Group[];
  groupRects: Map<string, GroupRect>;
  groupLinkSource: string | null;
  onOpenGroup?: (groupId: string, title: string) => void;
  onGroupLinkBtn: (grp: Group) => void;
  onDeleteGroup: (grp: Group) => void;
}) {
  return (
    <>
      {groups.map((grp) => {
        const r = groupRects.get(grp.id);
        if (!r) return null;
        const color = grp.color ?? "#5a6a7a";
        const isLinkSrc = groupLinkSource === grp.id;
        return (
          <div
            key={grp.id}
            className={`dag-group${isLinkSrc ? " link-src" : ""}`}
            style={{
              left: r.x,
              top: r.y,
              width: r.w,
              height: r.h,
              borderColor: color,
            }}
          >
            {/* 라벨 = 이름 + 명시적 아이콘 버튼(▤ 본문 / ⇢ 연결 / ✕ 삭제). 박스는 투과(pointer-events
                none)이고 라벨만 활성. 라벨 pointerdown 전파를 막아 캔버스가 연결모드를 즉시 비우지 않게 한다. */}
            <div
              className="dag-group-label"
              style={{ borderColor: color }}
              onPointerDown={(e) => e.stopPropagation()}
            >
              <span className="dag-group-name" style={{ color }} title={grp.name}>
                {grp.name}
              </span>
              <span className="dag-group-acts">
                <button
                  type="button"
                  className="dag-group-btn"
                  onClick={() => onOpenGroup?.(grp.id, grp.name)}
                  title="그룹 본문 열기"
                  aria-label="본문"
                >
                  ▤
                </button>
                <button
                  type="button"
                  className={`dag-group-btn${isLinkSrc ? " active" : ""}`}
                  onClick={() => onGroupLinkBtn(grp)}
                  title={
                    groupLinkSource === null
                      ? "연결 시작 — 이 그룹에서 화살표"
                      : isLinkSrc
                        ? "연결 취소"
                        : "여기로 연결 (화살표 생성)"
                  }
                  aria-label="연결"
                >
                  ⇢
                </button>
                <button
                  type="button"
                  className="dag-group-btn danger"
                  onClick={() => onDeleteGroup(grp)}
                  title="그룹 삭제"
                  aria-label="삭제"
                >
                  ✕
                </button>
              </span>
            </div>
          </div>
        );
      })}
    </>
  );
}
