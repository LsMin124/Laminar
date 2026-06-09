import { useEffect, useRef, useState } from "react";
import { EDGE_ZONE, PAN_SPEED, PX_PER_DAY } from "./dagGeometry";

export type DragMode = "move" | "resize-l" | "resize-r";
export interface DragState {
  id: string;
  mode: DragMode;
  x: number;
  y: number;
  w: number;
  offsetX: number;
  offsetY: number;
  rightX: number;
  moved: boolean;
  shift: boolean;
}

interface Geom {
  x: number;
  y: number;
  w: number;
}

/**
 * DAG 캔버스 드래그/리사이즈 + 가장자리 자동 스크롤(rAF) 메커닉.
 * 상태 없는 좌표 변환·포인터 추적만 담당한다 — 드롭 후 커밋/선택 같은 도메인 로직과
 * setDrag 타이밍은 컨테이너(DagCanvas)가 노출된 drag/setDrag로 직접 제어한다(동작 보존).
 */
export function useDagDrag() {
  const canvasRef = useRef<HTMLDivElement>(null);
  const [drag, setDrag] = useState<DragState | null>(null);
  const dragRef = useRef<DragState | null>(null);
  const lastPtRef = useRef<{ x: number; y: number } | null>(null);
  const panDirRef = useRef(0);
  const panRafRef = useRef<number | null>(null);

  // 드래그 상태 미러(rAF 패닝 루프가 최신 drag를 읽도록) + 언마운트 시 패닝 정리.
  useEffect(() => {
    dragRef.current = drag;
  }, [drag]);
  useEffect(() => () => stopPan(), []);

  /** 캔버스 로컬 좌표(스크롤 보정) — 더블클릭 생성·nub 연결도 공유. */
  function canvasPoint(clientX: number, clientY: number) {
    const el = canvasRef.current;
    if (!el) return { x: 0, y: 0 };
    const r = el.getBoundingClientRect();
    return { x: clientX - r.left + el.scrollLeft, y: clientY - r.top + el.scrollTop };
  }

  function applyPointerToDrag(clientX: number, clientY: number) {
    const p = canvasPoint(clientX, clientY);
    setDrag((d) => {
      if (!d) return d;
      if (d.mode === "move") {
        const nx = Math.max(0, p.x - d.offsetX);
        const ny = Math.max(0, p.y - d.offsetY);
        return {
          ...d,
          x: nx,
          y: ny,
          moved: d.moved || Math.abs(nx - d.x) > 2 || Math.abs(ny - d.y) > 2,
        };
      }
      if (d.mode === "resize-r") {
        const nw = Math.max(PX_PER_DAY, p.x - d.x);
        return { ...d, w: nw, moved: true };
      }
      const nx = Math.max(0, Math.min(p.x, d.rightX - PX_PER_DAY));
      return { ...d, x: nx, w: d.rightX - nx, moved: true };
    });
  }

  function stopPan() {
    if (panRafRef.current != null) {
      cancelAnimationFrame(panRafRef.current);
      panRafRef.current = null;
    }
    panDirRef.current = 0;
  }

  // 가장자리 자동 스크롤 루프 — 마지막 포인터 위치 기준으로 캔버스를 밀고 드래그 카드를 따라 이동.
  function panStep() {
    const el = canvasRef.current;
    if (!el || panDirRef.current === 0) {
      panRafRef.current = null;
      return;
    }
    const maxScroll = el.scrollWidth - el.clientWidth;
    const next = Math.max(0, Math.min(maxScroll, el.scrollLeft + panDirRef.current * PAN_SPEED));
    if (next === el.scrollLeft) {
      panRafRef.current = null; // 경계 도달 — 정지
      return;
    }
    el.scrollLeft = next;
    // 드래그 중이면 카드가 포인터를 따라가도록 재적용(호버 중엔 스크롤만).
    if (dragRef.current && lastPtRef.current) {
      applyPointerToDrag(lastPtRef.current.x, lastPtRef.current.y);
    }
    panRafRef.current = requestAnimationFrame(panStep);
  }

  // 포인터 X가 좌우 경계 근처면 패닝 방향 설정 + 루프 시작/중지 (드래그·호버 공용).
  function updateEdgePan(clientX: number) {
    const el = canvasRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const localX = clientX - rect.left;
    if (localX < EDGE_ZONE) panDirRef.current = -1;
    else if (localX > rect.width - EDGE_ZONE) panDirRef.current = 1;
    else panDirRef.current = 0;
    if (panDirRef.current !== 0) {
      if (panRafRef.current == null) panRafRef.current = requestAnimationFrame(panStep);
    } else {
      stopPan();
    }
  }

  /** 막대 몸통 드래그 시작 — 포인터 캡처 + 이동 모드 drag 시드(geom=드래그 시작 시 노드 위치). */
  function beginMove(e: React.PointerEvent<HTMLDivElement>, id: string, geom: Geom) {
    e.stopPropagation();
    const p = canvasPoint(e.clientX, e.clientY);
    e.currentTarget.setPointerCapture(e.pointerId);
    setDrag({
      id,
      mode: "move",
      x: geom.x,
      y: geom.y,
      w: geom.w,
      offsetX: p.x - geom.x,
      offsetY: p.y - geom.y,
      rightX: geom.x + geom.w,
      moved: false,
      shift: e.shiftKey,
    });
  }

  /** 좌/우 끝 핸들 드래그 시작 — 리사이즈 모드 drag 시드. */
  function beginResize(
    e: React.PointerEvent<HTMLDivElement>,
    id: string,
    geom: Geom,
    mode: DragMode,
  ) {
    e.stopPropagation();
    e.currentTarget.setPointerCapture(e.pointerId);
    setDrag({
      id,
      mode,
      x: geom.x,
      y: geom.y,
      w: geom.w,
      offsetX: 0,
      offsetY: 0,
      rightX: geom.x + geom.w,
      moved: false,
      shift: false,
    });
  }

  /** 드래그 중 포인터 이동 — 위치 갱신 + 가장자리 패닝(드래그 아닐 땐 무시). */
  function handleDragMove(e: React.PointerEvent<HTMLDivElement>) {
    if (!drag) return;
    lastPtRef.current = { x: e.clientX, y: e.clientY };
    applyPointerToDrag(e.clientX, e.clientY);
    updateEdgePan(e.clientX);
  }

  return {
    canvasRef,
    drag,
    setDrag,
    dragRef,
    lastPtRef,
    canvasPoint,
    beginMove,
    beginResize,
    handleDragMove,
    updateEdgePan,
    stopPan,
  };
}
