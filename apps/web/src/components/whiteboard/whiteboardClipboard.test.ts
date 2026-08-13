import { describe, expect, test } from "vitest";
import type { WhiteboardEdge, WhiteboardNode } from "../../lib/whiteboard";
import { parseClipboardText, serializeClipboard, snapshotSelection } from "./whiteboardClipboard";

function node(over: Partial<WhiteboardNode>): WhiteboardNode {
  return {
    id: "n1",
    tabId: "t1",
    kind: "MD",
    x: 0,
    y: 0,
    width: 200,
    height: 100,
    text: null,
    bodyMd: null,
    attrs: {},
    ...over,
  };
}

function edge(over: Partial<WhiteboardEdge>): WhiteboardEdge {
  return {
    id: "e1",
    tabId: "t1",
    fromNodeId: "n1",
    toNodeId: "n2",
    relationKind: "default",
    label: null,
    ...over,
  };
}

const rectOf = (n: WhiteboardNode) => ({ x: n.x, y: n.y, w: n.width ?? 240, h: n.height ?? 150 });

describe("snapshotSelection", () => {
  test("선택 노드를 좌상단 기준 상대 좌표로 담는다", () => {
    const nodes = [node({ id: "a", x: 100, y: 50 }), node({ id: "b", x: 400, y: 250 })];
    const snap = snapshotSelection(nodes, [], new Set(["a", "b"]), rectOf);
    expect(snap?.nodes.map((n) => [n.dx, n.dy])).toEqual([
      [0, 0],
      [300, 200],
    ]);
    expect(snap?.w).toBe(500);
    expect(snap?.h).toBe(300);
  });

  test("양끝이 모두 선택된 엣지만 포함한다", () => {
    const nodes = [node({ id: "a" }), node({ id: "b", x: 300 }), node({ id: "c", x: 600 })];
    const edges = [
      edge({ id: "e1", fromNodeId: "a", toNodeId: "b", label: "ok" }),
      edge({ id: "e2", fromNodeId: "b", toNodeId: "c" }),
    ];
    const snap = snapshotSelection(nodes, edges, new Set(["a", "b"]), rectOf);
    expect(snap?.edges).toEqual([{ from: 0, to: 1, label: "ok" }]);
  });

  test("선택이 비면 null", () => {
    expect(snapshotSelection([node({})], [], new Set(), rectOf)).toBeNull();
  });
});

describe("clipboard 직렬화", () => {
  test("serialize→parse 왕복 보존", () => {
    const nodes = [
      node({ id: "a", text: "제목", bodyMd: "본문" }),
      node({ id: "b", x: 300, kind: "IMAGE", attrs: { attachmentId: "att-1" } }),
    ];
    const snap = snapshotSelection(nodes, [edge({ fromNodeId: "a", toNodeId: "b" })], new Set(["a", "b"]), rectOf);
    if (!snap) throw new Error("snapshot 실패");
    expect(parseClipboardText(serializeClipboard(snap))).toEqual(snap);
  });

  test("스티키·도형·텍스트 노드도 왕복 보존된다 (WB-B)", () => {
    const nodes = [
      node({ id: "a", kind: "STICKY", bodyMd: "메모", attrs: { color: "amber" } }),
      node({ id: "b", x: 300, kind: "SHAPE", text: "단계", attrs: { color: "blue", shape: "ellipse" } }),
      node({ id: "c", x: 600, kind: "TEXT", bodyMd: "제목 텍스트", attrs: { color: "gray" } }),
    ];
    const snap = snapshotSelection(nodes, [], new Set(["a", "b", "c"]), rectOf);
    if (!snap) throw new Error("snapshot 실패");
    expect(parseClipboardText(serializeClipboard(snap))).toEqual(snap);
  });

  test("접두사 없는 텍스트·깨진 JSON·모르는 kind는 null", () => {
    expect(parseClipboardText("hello")).toBeNull();
    expect(parseClipboardText("laminar-wb:1:{broken")).toBeNull();
    expect(
      parseClipboardText('laminar-wb:1:{"nodes":[{"kind":"NOPE","dx":0,"dy":0}],"edges":[],"w":0,"h":0}'),
    ).toBeNull();
  });

  test("엣지 인덱스가 범위를 벗어나면 null", () => {
    const txt =
      'laminar-wb:1:{"nodes":[{"kind":"MD","dx":0,"dy":0,"width":null,"height":null,"text":null,"bodyMd":null,"attrs":{}}],"edges":[{"from":0,"to":5,"label":null}],"w":10,"h":10}';
    expect(parseClipboardText(txt)).toBeNull();
  });
});
