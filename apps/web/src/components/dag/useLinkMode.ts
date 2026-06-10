import { useRef, useState } from "react";
import { useCreateRelation } from "../../lib/cards";
import type { Card, Group, GroupRelation } from "../../lib/graphTypes";
import { useCreateGroupRelation } from "../../lib/groups";
import { BAR_H } from "./dagGeometry";

/**
 * 연결 모드 상태·제스처 훅 (DX-11 추출) — 카드 화살표(툴바 ⇢ 선택 모드 + 핸들 nub 드래그)와
 * 그룹 화살표(라벨 ⇢ 토글) 생성을 담당. 좌표 해석(nodeGeom·canvasPoint)은 컨테이너가 주입.
 */
export function useLinkMode({
  tabId,
  cards,
  groupRelations,
  nodeGeom,
  canvasPoint,
  onError,
}: {
  tabId: string;
  cards: Card[];
  groupRelations: GroupRelation[];
  nodeGeom: (c: Card) => { x: number; y: number; w: number };
  canvasPoint: (clientX: number, clientY: number) => { x: number; y: number };
  onError: (err: unknown) => void;
}) {
  const createRelation = useCreateRelation(tabId);
  const createGroupRelation = useCreateGroupRelation(tabId);

  const [linkSource, setLinkSource] = useState<string | null>(null);
  // 연결 핸들(nub) 드래그 — 임시 라인 좌표(sx,sy=출발 앵커, x,y=커서) + 출발 카드 id.
  const [linkLine, setLinkLine] = useState<{
    sx: number;
    sy: number;
    x: number;
    y: number;
  } | null>(null);
  const linkFromRef = useRef<string | null>(null);
  // 그룹 연결 모드 — 라벨 ⇢ 버튼으로 출발 그룹 지정, 대상 그룹 ⇢ 클릭 시 화살표 생성.
  const [groupLinkSource, setGroupLinkSource] = useState<string | null>(null);

  /** 툴바 ⇢ 모드에서 대상 카드 클릭 — 출발≠대상이면 관계 생성, 모드는 항상 해제. */
  function completeCardLink(targetId: string) {
    if (linkSource && linkSource !== targetId) {
      createRelation.mutate({ fromCardId: linkSource, toCardId: targetId }, { onError });
    }
    setLinkSource(null);
  }

  // 연결 핸들 nub: pointer capture로 드래그하다 다른 카드 위에서 놓으면 from→target 관계 생성.
  function onNubDown(e: React.PointerEvent<HTMLSpanElement>, c: Card) {
    e.stopPropagation();
    e.preventDefault();
    const g = nodeGeom(c);
    linkFromRef.current = c.id;
    setLinkLine({ sx: g.x + g.w, sy: g.y + BAR_H / 2, x: g.x + g.w, y: g.y + BAR_H / 2 });
    e.currentTarget.setPointerCapture(e.pointerId);
  }
  function onNubMove(e: React.PointerEvent<HTMLSpanElement>) {
    if (!linkFromRef.current) return;
    const p = canvasPoint(e.clientX, e.clientY);
    setLinkLine((l) => (l ? { ...l, x: p.x, y: p.y } : l));
  }
  function onNubUp(e: React.PointerEvent<HTMLSpanElement>) {
    const from = linkFromRef.current;
    linkFromRef.current = null;
    setLinkLine(null);
    if (!from) return;
    const p = canvasPoint(e.clientX, e.clientY);
    const target = cards.find((t) => {
      const tg = nodeGeom(t);
      return p.x >= tg.x && p.x <= tg.x + tg.w && p.y >= tg.y && p.y <= tg.y + BAR_H;
    });
    if (target && target.id !== from) {
      createRelation.mutate({ fromCardId: from, toCardId: target.id }, { onError });
    }
  }

  /**
   * 그룹 라벨 ⇢ 버튼 — 연결 모드 토글/완성. 처음 누르면 출발 그룹 지정(link-src 강조),
   * 다른 그룹의 ⇢를 누르면 화살표 생성(출발→대상, 중복·self 가드), 같은 그룹 ⇢ 재클릭은 취소.
   */
  function onGroupLinkBtn(grp: Group) {
    if (groupLinkSource === null) {
      setGroupLinkSource(grp.id);
      return;
    }
    if (groupLinkSource === grp.id) {
      setGroupLinkSource(null);
      return;
    }
    const exists = groupRelations.some(
      (gr) => gr.fromGroupId === groupLinkSource && gr.toGroupId === grp.id,
    );
    if (!exists) {
      createGroupRelation.mutate(
        { fromGroupId: groupLinkSource, toGroupId: grp.id },
        { onError },
      );
    }
    setGroupLinkSource(null);
  }

  return {
    linkSource,
    setLinkSource,
    linkLine,
    groupLinkSource,
    setGroupLinkSource,
    completeCardLink,
    onNubDown,
    onNubMove,
    onNubUp,
    onGroupLinkBtn,
  };
}
