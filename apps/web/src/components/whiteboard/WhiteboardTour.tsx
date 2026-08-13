import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { placeTourPopup, type TourRect } from "./whiteboardTourPlacement";

type TourStep = { target?: string; title: string; body: string };

/** 코치마크 단계 — target은 캔버스 내부의 data-tour 셀렉터, 없으면 중앙 안내 팝업. */
const STEPS: readonly TourStep[] = [
  {
    title: "화이트보드 둘러보기",
    body: "자유 배치 캔버스입니다. 노드를 만들고 화살표로 관계를 표현합니다. 다음을 눌러 30초 안내를 진행하세요.",
  },
  {
    target: '[data-tour="add-node"]',
    title: "노드 만들기",
    body: "+ 노드 버튼 또는 빈 캔버스를 더블클릭하면 그 자리에 노드가 생깁니다. 노드를 더블클릭하면 제목과 마크다운 본문을 편집합니다.",
  },
  {
    target: '[data-tour="add-image"]',
    title: "이미지 넣기",
    body: "+ 이미지로 파일을 선택하거나, 이미지를 캔버스에 드래그&드롭 또는 Ctrl+V로 붙여넣으면 됩니다.",
  },
  {
    title: "이동과 선택",
    body: "노드는 드래그로 옮깁니다. 빈 배경을 드래그하면 여러 노드를 한 번에 선택하고(Shift+클릭=추가/제외), Delete=삭제 · Ctrl+C/V=복사 · Ctrl+D=복제입니다.",
  },
  {
    title: "연결하기",
    body: "노드에 마우스를 올리면 오른쪽에 주황 점이 나타납니다. 이 점을 드래그해 다른 노드 위에 놓으면 화살표로 연결되고, 화살표 가운데 라벨을 클릭해 관계 이름을 붙입니다.",
  },
  {
    target: '[data-tour="zoom"]',
    title: "화면 이동·줌",
    body: "휠=스크롤, Ctrl+휠=커서 기준 줌, Space나 휠클릭 드래그=화면 끌기입니다. 이 버튼들로 줌 · 전체 맞춤(Shift+1) · 100%(Ctrl+0)도 됩니다.",
  },
  {
    target: '[data-tour="help"]',
    title: "언제든 다시 보기",
    body: "이 안내는 ? 버튼으로 다시 열 수 있습니다. 이제 자유롭게 써보세요!",
  },
];

const HOLE_PAD = 6;

/**
 * 화이트보드 온보딩 투어 — 대상 요소를 스팟라이트(구멍 뚫린 딤)로 하이라이트하고
 * 옆에 작은 팝업으로 단계별 안내. 배경 클릭·→=다음, ←=이전, Esc=닫기.
 * 좌표는 전부 .wb 컨테이너 기준(absolute) — fixed는 조상 transform에 깨질 수 있어 피한다.
 */
export function WhiteboardTour({
  containerRef,
  onClose,
}: {
  containerRef: React.RefObject<HTMLDivElement | null>;
  onClose: () => void;
}) {
  const [step, setStep] = useState(0);
  const [hole, setHole] = useState<TourRect | null>(null);
  const [pos, setPos] = useState<{ x: number; y: number } | null>(null);
  const popupRef = useRef<HTMLDivElement>(null);
  const current = STEPS[step];
  const last = step === STEPS.length - 1;

  const measure = useCallback(() => {
    const wb = containerRef.current;
    const popup = popupRef.current;
    if (!wb || !popup) return;
    const wbRect = wb.getBoundingClientRect();
    const target = current.target ? wb.querySelector(current.target) : null;
    let nextHole: TourRect | null = null;
    if (target) {
      const r = target.getBoundingClientRect();
      nextHole = {
        x: r.left - wbRect.left - HOLE_PAD,
        y: r.top - wbRect.top - HOLE_PAD,
        w: r.width + HOLE_PAD * 2,
        h: r.height + HOLE_PAD * 2,
      };
    }
    setHole(nextHole);
    setPos(
      placeTourPopup(
        nextHole,
        { w: popup.offsetWidth, h: popup.offsetHeight },
        { w: wbRect.width, h: wbRect.height },
      ),
    );
  }, [containerRef, current.target]);

  // 팝업 내용이 렌더된 뒤 실제 크기로 배치해야 하므로 layoutEffect(페인트 전) 사용.
  useLayoutEffect(() => {
    measure();
  }, [measure]);
  useEffect(() => {
    window.addEventListener("resize", measure);
    return () => window.removeEventListener("resize", measure);
  }, [measure]);

  const next = useCallback(() => {
    if (last) onClose();
    else setStep((s) => s + 1);
  }, [last, onClose]);
  const prev = useCallback(() => setStep((s) => Math.max(0, s - 1)), []);

  // 캔버스 단축키(화살표 nudge 등)보다 먼저 받도록 capture 단계에서 소비한다.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") {
        e.stopPropagation();
        onClose();
      } else if (e.key === "ArrowRight") {
        e.stopPropagation();
        next();
      } else if (e.key === "ArrowLeft") {
        e.stopPropagation();
        prev();
      }
    }
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [next, prev, onClose]);

  return (
    <div
      className={`wb-tour${hole ? "" : " dim"}`}
      onPointerDown={(e) => e.stopPropagation()}
      onDoubleClick={(e) => e.stopPropagation()}
      onClick={(e) => {
        e.stopPropagation();
        next();
      }}
    >
      {hole && (
        <div
          className="wb-tour-hole"
          style={{ left: hole.x, top: hole.y, width: hole.w, height: hole.h }}
        />
      )}
      <div
        className="wb-tour-pop"
        ref={popupRef}
        role="dialog"
        aria-modal="true"
        aria-label="화이트보드 사용 안내"
        style={{ left: pos?.x ?? 0, top: pos?.y ?? 0, visibility: pos ? "visible" : "hidden" }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="wb-tour-head">
          <span className="wb-tour-title">{current.title}</span>
          <span className="wb-tour-count">
            {step + 1}/{STEPS.length}
          </span>
        </div>
        <div className="wb-tour-body">{current.body}</div>
        <div className="wb-tour-foot">
          <button type="button" className="ghost" onClick={onClose}>
            건너뛰기
          </button>
          {step > 0 && (
            <button type="button" onClick={prev}>
              이전
            </button>
          )}
          <button type="button" className="primary" onClick={next}>
            {last ? "완료" : "다음"}
          </button>
        </div>
      </div>
    </div>
  );
}
