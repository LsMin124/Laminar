import { defineConfig } from "vitest/config";

/**
 * 단위테스트 러너 설정 (DX-13②). include를 src/*.test.*로 한정 —
 * e2e의 playwright *.spec.*가 vitest에 잘못 수집되지 않도록.
 */
export default defineConfig({
  test: {
    include: ["src/**/*.test.{ts,tsx}"],
    environment: "node",
  },
});
