import { describe, expect, test } from "vitest";
import { WbHistory } from "./whiteboardHistory";

function recordingCommand(log: string[], name: string) {
  return { undo: () => log.push(`undo:${name}`), redo: () => log.push(`redo:${name}`) };
}

describe("WbHistory", () => {
  test("undo는 LIFO로 실행되고 redo는 반대 순서로 되돌린다", () => {
    const log: string[] = [];
    const h = new WbHistory();
    h.push(recordingCommand(log, "a"));
    h.push(recordingCommand(log, "b"));
    expect(h.undo()).toBe(true);
    expect(h.undo()).toBe(true);
    expect(h.redo()).toBe(true);
    expect(log).toEqual(["undo:b", "undo:a", "redo:a"]);
  });

  test("빈 스택 undo/redo는 false를 반환하고 아무것도 하지 않는다", () => {
    const h = new WbHistory();
    expect(h.undo()).toBe(false);
    expect(h.redo()).toBe(false);
  });

  test("undo 뒤 새 push는 redo 스택을 비운다(타임라인 분기 방지)", () => {
    const log: string[] = [];
    const h = new WbHistory();
    h.push(recordingCommand(log, "a"));
    h.undo();
    h.push(recordingCommand(log, "b"));
    expect(h.redo()).toBe(false);
  });

  test("한도를 넘으면 가장 오래된 명령부터 버린다", () => {
    const log: string[] = [];
    const h = new WbHistory(2);
    h.push(recordingCommand(log, "a"));
    h.push(recordingCommand(log, "b"));
    h.push(recordingCommand(log, "c"));
    expect(h.undo()).toBe(true);
    expect(h.undo()).toBe(true);
    expect(h.undo()).toBe(false);
    expect(log).toEqual(["undo:c", "undo:b"]);
  });
});
