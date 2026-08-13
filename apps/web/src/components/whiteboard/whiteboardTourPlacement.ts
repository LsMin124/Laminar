/** 화이트보드 온보딩 투어의 코치마크 배치 — 순수 계산. 좌표는 .wb 컨테이너 기준 px. */
export type TourRect = { x: number; y: number; w: number; h: number };

const GAP = 12;
const MARGIN = 12;

/**
 * 팝업 위치 결정 — 스팟라이트(hole) 아래 우선, 아래 공간이 부족하면 위로 뒤집고,
 * 좌우는 컨테이너 안쪽으로 클램프한다. hole이 없으면(중앙 안내 단계) 컨테이너 중앙.
 */
export function placeTourPopup(
  hole: TourRect | null,
  popup: { w: number; h: number },
  bounds: { w: number; h: number },
): { x: number; y: number } {
  if (hole === null) {
    return {
      x: Math.max(MARGIN, (bounds.w - popup.w) / 2),
      y: Math.max(MARGIN, (bounds.h - popup.h) / 2),
    };
  }
  const below = hole.y + hole.h + GAP;
  const fitsBelow = below + popup.h + MARGIN <= bounds.h;
  const y = fitsBelow ? below : Math.max(MARGIN, hole.y - GAP - popup.h);
  const x = Math.min(Math.max(MARGIN, hole.x), Math.max(MARGIN, bounds.w - popup.w - MARGIN));
  return { x, y };
}
