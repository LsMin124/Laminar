import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { markAuthenticated, registerRefresher, stopSilentRefresh } from "./silentRefresh";

const TTL_900 = 900; // 서버 기본 access TTL 15m
const FIRE_AT_MS = (900 - 120) * 1000; // 만료 2분 전 = 13m

describe("silentRefresh", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    stopSilentRefresh();
    vi.useRealTimers();
  });

  it("TTL - 리드(2분) 시점에 refresher를 1회 호출한다", async () => {
    const refresher = vi.fn(async () => true);
    registerRefresher(refresher);

    markAuthenticated(TTL_900);

    await vi.advanceTimersByTimeAsync(FIRE_AT_MS - 1);
    expect(refresher).toHaveBeenCalledTimes(0);
    await vi.advanceTimersByTimeAsync(1);
    expect(refresher).toHaveBeenCalledTimes(1);
  });

  it("성공하면 마지막 TTL로 재무장한다", async () => {
    const refresher = vi.fn(async () => true);
    registerRefresher(refresher);

    markAuthenticated(TTL_900);

    await vi.advanceTimersByTimeAsync(FIRE_AT_MS);
    expect(refresher).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(FIRE_AT_MS);
    expect(refresher).toHaveBeenCalledTimes(2);
  });

  it("실패(false)하면 멈춘다 — 이후는 반응적 401 경로의 몫", async () => {
    const refresher = vi.fn(async () => false);
    registerRefresher(refresher);

    markAuthenticated(TTL_900);

    await vi.advanceTimersByTimeAsync(FIRE_AT_MS);
    expect(refresher).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(60 * 60_000);
    expect(refresher).toHaveBeenCalledTimes(1);
  });

  it("markAuthenticated 재호출은 기존 타이머를 대체한다 — 이중 발화 없음", async () => {
    const refresher = vi.fn(async () => true);
    registerRefresher(refresher);

    markAuthenticated(TTL_900);
    await vi.advanceTimersByTimeAsync(5 * 60_000);
    markAuthenticated(TTL_900); // 새 인증 응답 도착 가정 — 타이머 리셋

    // 원래 타이머의 발화 시점(13m)을 지나도 미발화
    await vi.advanceTimersByTimeAsync(8 * 60_000);
    expect(refresher).toHaveBeenCalledTimes(0);
    // 리셋 기준 13m 도달 시 발화
    await vi.advanceTimersByTimeAsync(5 * 60_000);
    expect(refresher).toHaveBeenCalledTimes(1);
  });

  it("TTL이 리드보다 짧으면 하한(30s)으로 스케줄한다", async () => {
    const refresher = vi.fn(async () => true);
    registerRefresher(refresher);

    markAuthenticated(60);

    await vi.advanceTimersByTimeAsync(30_000);
    expect(refresher).toHaveBeenCalledTimes(1);
  });

  it("무효 TTL(0·NaN)은 무시한다", async () => {
    const refresher = vi.fn(async () => true);
    registerRefresher(refresher);

    markAuthenticated(0);
    markAuthenticated(Number.NaN);

    await vi.advanceTimersByTimeAsync(60 * 60_000);
    expect(refresher).toHaveBeenCalledTimes(0);
  });
});
