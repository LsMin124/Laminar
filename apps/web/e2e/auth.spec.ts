import { test, expect } from "@playwright/test";

/**
 * 인증 화면 smoke test (백엔드 미연결 환경에서도 동작).
 *
 * 백엔드가 떠 있지 않아도 LoginPage/SignupPage UI는 클라이언트만으로 렌더링되어야 함.
 */

test.describe("auth screens", () => {
  test("login screen 렌더링", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { name: /로그인/i })).toBeVisible();
  });

  test("회원가입 화면 전환", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("button", { name: /회원가입/i }).first().click();
    await expect(
      page.getByRole("heading", { name: /회원가입/i }),
    ).toBeVisible();
  });
});
