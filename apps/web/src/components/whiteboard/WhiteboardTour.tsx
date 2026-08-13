import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { placeTourPopup, type TourRect } from "./whiteboardTourPlacement";

type TourStep = { target?: string; title: string; body: ReactNode };

/** 키·마우스 제스처 칩 — 안내 본문에서 단축키를 본문 텍스트와 시각적으로 구분한다. */
function K({ children }: { children: ReactNode }) {
  return <kbd className="wb-kbd">{children}</kbd>;
}

/** 코치마크 단계 — target은 캔버스 내부의 data-tour 셀렉터, 없으면 중앙 안내 팝업. */
const STEPS: readonly TourStep[] = [
  {
    title: "화이트보드 둘러보기",
    body: (
      <>
        <p>자유 배치 캔버스입니다. 노드를 만들고 화살표로 관계를 표현합니다.</p>
        <ul className="wb-tour-list">
          <li>
            <K>→</K> 다음 · <K>←</K> 이전 · <K>Esc</K> 닫기
          </li>
        </ul>
      </>
    ),
  },
  {
    target: '[data-tour="add-node"]',
    title: "노드 만들기",
    body: (
      <ul className="wb-tour-list">
        <li>
          빈 캔버스 <K>더블클릭</K> — 그 자리에 새 노드
        </li>
        <li>
          노드 <K>더블클릭</K> — 제목·마크다운 본문 편집
        </li>
        <li>스티키 · 도형 · 텍스트 · 섹션은 옆 버튼으로</li>
      </ul>
    ),
  },
  {
    target: '[data-tour="add-image"]',
    title: "이미지 넣기",
    body: (
      <ul className="wb-tour-list">
        <li>버튼을 눌러 파일 선택</li>
        <li>
          캔버스에 <K>드래그&드롭</K>
        </li>
        <li>
          <K>Ctrl</K>+<K>V</K> — 클립보드 이미지 붙여넣기
        </li>
      </ul>
    ),
  },
  {
    title: "이동과 선택",
    body: (
      <ul className="wb-tour-list">
        <li>
          노드 <K>드래그</K> — 이동
        </li>
        <li>
          빈 배경 <K>드래그</K> — 여러 개 선택 (<K>Shift</K>+클릭 = 추가/제외)
        </li>
        <li>
          <K>Delete</K> 삭제 · <K>Ctrl</K>+<K>C</K>/<K>V</K> 복사 · <K>Ctrl</K>+<K>D</K> 복제
        </li>
        <li>
          <K>Ctrl</K>+<K>Z</K> 실행 취소 · <K>Ctrl</K>+<K>Shift</K>+<K>Z</K> 다시 실행
        </li>
      </ul>
    ),
  },
  {
    title: "연결하기",
    body: (
      <ul className="wb-tour-list">
        <li>노드에 마우스를 올리면 오른쪽에 주황 점</li>
        <li>
          그 점을 다른 노드로 <K>드래그</K> — 화살표 연결
        </li>
        <li>화살표 가운데 라벨 클릭 — 관계 이름 붙이기</li>
        <li>화살표 끝점을 끌면 다른 노드로 재연결</li>
      </ul>
    ),
  },
  {
    target: '[data-tour="zoom"]',
    title: "화면 이동·줌",
    body: (
      <ul className="wb-tour-list">
        <li>
          <K>휠</K> 스크롤 · <K>Ctrl</K>+<K>휠</K> 커서 기준 줌
        </li>
        <li>
          <K>Space</K> 또는 <K>휠클릭</K> 드래그 — 화면 끌기
        </li>
        <li>
          <K>Shift</K>+<K>1</K> 전체 맞춤 · <K>Ctrl</K>+<K>0</K> 100%
        </li>
      </ul>
    ),
  },
  {
    target: '[data-tour="help"]',
    title: "언제든 다시 보기",
    body: (
      <p>
        이 안내는 <K>?</K> 버튼으로 다시 열 수 있습니다. 이제 자유롭게 써보세요!
      </p>
    ),
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
