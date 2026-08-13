/**
 * 화이트보드 색상 팔레트 · 도형 종류 — attrs.color / attrs.shape에 id 문자열로 저장한다.
 * hex 대신 id를 저장하므로 팔레트 색값이 바뀌어도 기존 노드가 자동으로 따라온다.
 */
import type { WhiteboardNode } from "../../lib/whiteboard";

export interface WbPaletteEntry {
  id: string;
  /** 채움색 — 스티키·도형은 배경, 텍스트 노드는 글자색. */
  fill: string;
  /** 채움 위 글자색. */
  ink: string;
}

export const WB_PALETTE: readonly WbPaletteEntry[] = [
  { id: "gray", fill: "#9aa0a6", ink: "#1d1d1d" },
  { id: "orange", fill: "#d97757", ink: "#1d1d1d" },
  { id: "amber", fill: "#d9b357", ink: "#1d1d1d" },
  { id: "green", fill: "#7fb069", ink: "#1d1d1d" },
  { id: "teal", fill: "#5fb0a5", ink: "#1d1d1d" },
  { id: "blue", fill: "#6f9fd8", ink: "#1d1d1d" },
  { id: "purple", fill: "#a58fd8", ink: "#1d1d1d" },
  { id: "rose", fill: "#d87f9f", ink: "#1d1d1d" },
];

/** kind별 기본 색 id — 생성 시 attrs.color에 기록된다. */
export const DEFAULT_COLOR: Record<"STICKY" | "SHAPE" | "TEXT", string> = {
  STICKY: "amber",
  SHAPE: "blue",
  TEXT: "gray",
};

/** attrs.color(id)를 팔레트 항목으로 — 모르는 id·비문자열은 kind 기본색, 그것도 없으면 gray. */
export function paletteEntry(colorId: unknown, kind: WhiteboardNode["kind"]): WbPaletteEntry {
  const requested = typeof colorId === "string" ? colorId : null;
  const fallback =
    kind === "STICKY" || kind === "SHAPE" || kind === "TEXT" ? DEFAULT_COLOR[kind] : "gray";
  return (
    WB_PALETTE.find((p) => p.id === requested) ??
    WB_PALETTE.find((p) => p.id === fallback) ??
    WB_PALETTE[0]
  );
}

export type WbShape = "rect" | "ellipse" | "diamond";

/** attrs.shape 검증 — 알 수 없는 값은 rect. */
export function shapeOf(attrs: Record<string, unknown>): WbShape {
  const s = attrs.shape;
  return s === "ellipse" || s === "diamond" ? s : "rect";
}

/** 색을 입힐 수 있는 노드 종류 — 선택 팔레트 바 노출 조건. */
export const COLORABLE_KINDS: ReadonlySet<WhiteboardNode["kind"]> = new Set([
  "STICKY",
  "SHAPE",
  "TEXT",
]);
