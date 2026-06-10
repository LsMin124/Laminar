import { describe, expect, test } from "vitest";
import { slugify } from "./slug";

describe("slugify", () => {
  test("영문/숫자는 소문자-하이픈 + 6자 suffix", () => {
    expect(slugify("Hello World 2")).toMatch(/^hello-world-2-[a-z0-9]{6}$/);
  });

  test("특수문자·연속 공백은 하이픈 1개로, 양끝은 트림", () => {
    expect(slugify("  A  --  B!! ")).toMatch(/^a-b-[a-z0-9]{6}$/);
  });

  test("a-z0-9가 없으면(한글 등) tab- fallback", () => {
    expect(slugify("실험 보드")).toMatch(/^tab-[a-z0-9]{6}$/);
  });

  test("호출마다 suffix가 달라 이름 충돌을 피한다", () => {
    expect(slugify("x")).not.toBe(slugify("x"));
  });
});
