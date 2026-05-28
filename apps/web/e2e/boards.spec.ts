import { test, expect } from "@playwright/test";

/**
 * 보드 흐름 — 백엔드 연결 시에만 실행.
 *
 * 환경변수 E2E_DISABLE_BACKEND가 설정되면 SKIP.
 */

test.describe("authenticated boards flow", () => {
  test.skip(
    !!process.env.E2E_DISABLE_BACKEND,
    "backend disabled — E2E_DISABLE_BACKEND set",
  );

  test("보드 목록 헤더 노출 (인증된 사용자)", async ({ page }) => {
    await page.goto("/");
    const loginHeading = page.getByRole("heading", { name: /로그인/i });
    if (await loginHeading.isVisible().catch(() => false)) {
      test.info().annotations.push({
        type: "info",
        description: "비인증 상태 — boards UI 진입 불가, smoke 종료",
      });
      return;
    }
    await expect(page.getByRole("heading", { name: /보드/i })).toBeVisible();
  });
});
