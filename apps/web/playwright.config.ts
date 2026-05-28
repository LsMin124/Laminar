import { defineConfig, devices } from "@playwright/test";

/**
 * Playwright 설정 — Laminar 핵심 user journey E2E.
 *
 * 로컬 실행: pnpm e2e
 * CI 실행: pnpm e2e:ci (headless + retries)
 *
 * 백엔드/프론트 같이 띄울 필요 — webServer 항목으로 Vite dev server만 기동.
 * 백엔드는 별도로 띄워야 함 (CI는 별도 job · 로컬은 ./gradlew bootRun).
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? "github" : "list",
  timeout: 30_000,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:5173",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: "pnpm dev",
        url: "http://localhost:5173",
        reuseExistingServer: !process.env.CI,
        timeout: 60_000,
      },
});
