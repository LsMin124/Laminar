/**
 * 선제(silent) access 갱신 스케줄러 — G1.
 *
 * access TTL(서버 AuthResponse.accessTtlSeconds가 정본 — FE 거울 상수 금지) 만료 전에 백그라운드로
 * refresh를 호출해 토큰쌍을 회전시킨다. 사용자는 반응적 401 왕복을 보지 않는다. 타이머가 빗나가면
 * (탭 슬립·일시 오류·me 기준 과대평가) api.ts의 반응적 401→refresh 경로가 안전망으로 동작한다.
 *
 * api.ts가 refresher를 주입(registerRefresher)한다 — 본 모듈이 api.ts를 import하면 순환이 생기므로
 * 의존 방향을 api.ts→silentRefresh 단방향으로 고정.
 */

const REFRESH_LEAD_SECONDS = 120; // 만료 2분 전 회전
const MIN_DELAY_MS = 30_000; // TTL이 리드보다 짧아도 폭주하지 않는 하한

type Refresher = () => Promise<boolean>;

let refresher: Refresher | null = null;
let timer: ReturnType<typeof setTimeout> | null = null;
let lastTtlSeconds = 0;

export function registerRefresher(fn: Refresher): void {
  refresher = fn;
}

/** 인증 응답(login·signup·me·refresh) 수신 시 호출 — 새 TTL 기준으로 선제 갱신 타이머를 재설정. */
export function markAuthenticated(accessTtlSeconds: number): void {
  if (!Number.isFinite(accessTtlSeconds) || accessTtlSeconds <= 0) return;
  lastTtlSeconds = accessTtlSeconds;
  schedule();
}

/** 세션 종료(깨끗한 refresh 401) 시 호출 — 다음 인증 응답까지 침묵. */
export function stopSilentRefresh(): void {
  if (timer !== null) clearTimeout(timer);
  timer = null;
}

function schedule(): void {
  stopSilentRefresh();
  const delayMs = Math.max((lastTtlSeconds - REFRESH_LEAD_SECONDS) * 1000, MIN_DELAY_MS);
  timer = setTimeout(() => void tick(), delayMs);
}

async function tick(): Promise<void> {
  if (!refresher) return;
  let ok = false;
  try {
    ok = await refresher();
  } catch {
    ok = false; // 일시 오류 — 반응적 경로가 안전망
  }
  // 성공 시 재무장. refresh 응답 처리(api.ts)도 markAuthenticated를 부르지만, 탭 간 디듀프로 응답
  // 본문 없이 true가 돌아오는 경우가 있어 마지막 TTL로도 재스케줄한다(중복은 schedule이 정리).
  // 실패 = refresh 토큰 무효(로그아웃 상태) — 멈추고, 이후는 반응적 401 경로가 처리.
  if (ok) schedule();
}
