/**
 * URL 라우트 구독 훅 + 내비게이션 (DX-3) — history API 직결, 라우터 의존성 0.
 *
 * pushState/replaceState는 popstate를 울리지 않으므로 커스텀 이벤트로 구독자에게 알린다.
 * 전환·열기 = pushRoute(뒤로가기로 복귀 가능), 보정·폴백 = replaceRoute(히스토리 오염 방지).
 */
import { useMemo, useSyncExternalStore } from "react";
import { formatRoute, parseRoute, type Route } from "./route";

const NAV_EVENT = "laminar:navigate";

function subscribe(onChange: () => void): () => void {
  window.addEventListener("popstate", onChange);
  window.addEventListener(NAV_EVENT, onChange);
  return () => {
    window.removeEventListener("popstate", onChange);
    window.removeEventListener(NAV_EVENT, onChange);
  };
}

function snapshot(): string {
  return window.location.pathname + window.location.search;
}

export function useRoute(): Route {
  const loc = useSyncExternalStore(subscribe, snapshot);
  return useMemo(() => {
    const q = loc.indexOf("?");
    return q < 0 ? parseRoute(loc) : parseRoute(loc.slice(0, q), loc.slice(q));
  }, [loc]);
}

export function pushRoute(next: Route): void {
  navigate(next, false);
}

export function replaceRoute(next: Route): void {
  navigate(next, true);
}

function navigate(next: Route, replace: boolean): void {
  const url = formatRoute(next);
  if (url === snapshot()) return;
  if (replace) window.history.replaceState(null, "", url);
  else window.history.pushState(null, "", url);
  window.dispatchEvent(new Event(NAV_EVENT));
}
