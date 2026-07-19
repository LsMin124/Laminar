import { useEffect, useRef } from "react";

export interface WhiteboardShortcutActions {
  /** false면 Space를 제외한 단축키 무시(노드 편집 중 등). */
  enabled: boolean;
  onDeleteSelection: () => void;
  onSelectAll: () => void;
  onEscape: () => void;
  onDuplicate: () => void;
  onCopy: () => void;
  onNudge: (dx: number, dy: number) => void;
  onZoomFit: () => void;
  onZoom100: () => void;
  onSpaceChange: (held: boolean) => void;
}

function isTypingTarget(t: EventTarget | null): boolean {
  const el = t as HTMLElement | null;
  return !!el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA" || el.isContentEditable);
}

/** 단축키가 유효한 포커스 범위 — 문서 body(포커스 없음) 또는 캔버스 내부. 버튼 등 위젯 포커스는 제외. */
function isShortcutScope(t: EventTarget | null): boolean {
  const el = t as HTMLElement | null;
  if (!el) return false;
  if (el === document.body) return true;
  return typeof el.closest === "function" && el.closest(".wb") !== null;
}

const NUDGE_STEP = 2;
const NUDGE_STEP_LARGE = 16;

/**
 * 화이트보드 전역 단축키(FigJam 준거) — Delete=삭제, Ctrl+A=전체 선택, Ctrl+D=복제, Ctrl+C=복사,
 * 화살표=이동(Shift=크게), Shift+1=전체 맞춤, Ctrl+0=100%, Space=손 도구(팬).
 * 붙여넣기는 window paste 이벤트에서 별도 처리(이미지/노드 스냅샷 공용). 레이아웃 무관하게 e.code 사용.
 */
export function useWhiteboardShortcuts(actions: WhiteboardShortcutActions): void {
  const ref = useRef(actions);
  useEffect(() => {
    ref.current = actions;
  });

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      const a = ref.current;
      if (isTypingTarget(e.target) || !isShortcutScope(e.target)) return;
      if (e.code === "Space") {
        e.preventDefault();
        a.onSpaceChange(true);
        return;
      }
      if (!a.enabled) return;
      const mod = e.ctrlKey || e.metaKey;
      if (e.key === "Escape") {
        a.onEscape();
        return;
      }
      if (e.key === "Delete" || e.key === "Backspace") {
        e.preventDefault();
        a.onDeleteSelection();
        return;
      }
      if (mod && e.code === "KeyA") {
        e.preventDefault();
        a.onSelectAll();
        return;
      }
      if (mod && e.code === "KeyD") {
        e.preventDefault();
        a.onDuplicate();
        return;
      }
      if (mod && e.code === "KeyC") {
        a.onCopy();
        return;
      }
      if (mod && e.code === "Digit0") {
        e.preventDefault();
        a.onZoom100();
        return;
      }
      if (e.shiftKey && e.code === "Digit1") {
        a.onZoomFit();
        return;
      }
      if (e.key === "ArrowLeft" || e.key === "ArrowRight" || e.key === "ArrowUp" || e.key === "ArrowDown") {
        e.preventDefault();
        const step = e.shiftKey ? NUDGE_STEP_LARGE : NUDGE_STEP;
        const dx = e.key === "ArrowLeft" ? -step : e.key === "ArrowRight" ? step : 0;
        const dy = e.key === "ArrowUp" ? -step : e.key === "ArrowDown" ? step : 0;
        a.onNudge(dx, dy);
      }
    }
    function onKeyUp(e: KeyboardEvent) {
      if (e.code === "Space") ref.current.onSpaceChange(false);
    }
    function onBlur() {
      // 창 전환 중 keyup 유실로 손 도구가 눌린 채 남는 것 방지.
      ref.current.onSpaceChange(false);
    }
    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("keyup", onKeyUp);
    window.addEventListener("blur", onBlur);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
      window.removeEventListener("blur", onBlur);
    };
  }, []);
}
