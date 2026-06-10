import { useMemo } from "react";
import { MS_DAY, todayUtc } from "../../lib/dateUtil";
import { LEFT_PAD, PX_PER_DAY } from "./dagGeometry";

/**
 * 날짜축 — 세로선(전체 높이, surface)과 라벨(sticky 헤더)을 분리 렌더 (DX-11 추출).
 * origin/일수에만 의존하므로 메모이즈 — 카드 드래그 재렌더에 축이 끌려가지 않는다.
 */
export function DagAxis({
  days,
  originMs,
  maxX,
}: {
  days: number;
  originMs: number;
  maxX: number;
}) {
  const gridLines = useMemo(
    () =>
      Array.from({ length: days }, (_, i) => (
        <div key={`gl-${i}`} className="dag-gridline" style={{ left: LEFT_PAD + i * PX_PER_DAY }} />
      )),
    [days],
  );
  const axisLabels = useMemo(() => {
    const t = todayUtc();
    return Array.from({ length: days }, (_, i) => {
      const dayMs = originMs + i * MS_DAY;
      const d = new Date(dayMs);
      return (
        <div
          key={`ax-${i}`}
          className={`dag-axis-label${dayMs === t ? " today" : ""}`}
          style={{ left: LEFT_PAD + i * PX_PER_DAY }}
        >
          {`${d.getUTCMonth() + 1}/${d.getUTCDate()}`}
        </div>
      );
    });
  }, [days, originMs]);

  return (
    <>
      {gridLines}
      <div className="dag-axis" style={{ width: maxX }}>
        {axisLabels}
      </div>
    </>
  );
}
