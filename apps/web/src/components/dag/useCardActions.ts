import { useCreateCard, useDeleteCard, useDeleteRelation, useUpdateCard, useUpdateRelation } from "../../lib/cards";
import { useSetCardCategory } from "../../lib/categories";
import type { Card, CardRelation, Group, GroupRelation } from "../../lib/graphTypes";
import { useDeleteGroup, useDeleteGroupRelation, useUpdateGroupRelation } from "../../lib/groups";
import { apiErrorMessage } from "../../lib/apiErrors";
import { useDialogs } from "../ui/DialogProvider";

/**
 * 카드/관계/그룹 액션 훅 (DX-11 추출) — 다이얼로그 확인·프롬프트와 mutation 호출을 묶는다.
 * 드래그 커밋(moveCard)·선택 토글 같은 캔버스 메커닉은 컨테이너 소유 — 여기는 "버튼이 하는 일"만.
 */
export function useCardActions({
  tabId,
  selectedIds,
  setSelectedIds,
  closeNewCard,
}: {
  tabId: string;
  selectedIds: Set<string>;
  setSelectedIds: (next: Set<string>) => void;
  closeNewCard: () => void;
}) {
  const createCard = useCreateCard(tabId);
  const updateCard = useUpdateCard(tabId);
  const deleteCard = useDeleteCard(tabId);
  const deleteRelation = useDeleteRelation(tabId);
  const updateRelation = useUpdateRelation(tabId);
  const deleteGroup = useDeleteGroup(tabId);
  const deleteGroupRelation = useDeleteGroupRelation(tabId);
  const updateGroupRelation = useUpdateGroupRelation(tabId);
  const setCardCategory = useSetCardCategory(tabId);
  const dialogs = useDialogs();

  async function reportError(err: unknown) {
    // 오류 code 기반 매핑(lib/apiErrors) — 메시지 문자열 매칭 금지(DX-4).
    const msg = apiErrorMessage(err, "작업에 실패했습니다.");
    await dialogs.alert({ title: "처리 불가", message: msg });
  }

  async function onEditTitle(c: Card) {
    const title = await dialogs.prompt({ title: "카드 제목 편집", defaultValue: c.title });
    if (title && title.trim() && title !== c.title) {
      updateCard.mutate({ cardId: c.id, title: title.trim() });
    }
  }

  /** 시간 설정 — HH:MM 입력 시 allDay=false+startTime, 비우면 종일(allDay=true). */
  async function onSetTime(c: Card) {
    const current = c.allDay ? "" : (c.startTime?.slice(0, 5) ?? "");
    const v = await dialogs.prompt({
      title: "시간 설정",
      message: "HH:MM 형식 (비우면 종일)",
      placeholder: "14:00",
      defaultValue: current,
    });
    if (v === null) return;
    const trimmed = v.trim();
    if (!trimmed) {
      updateCard.mutate({ cardId: c.id, allDay: true });
      return;
    }
    const m = /^([01]?\d|2[0-3]):([0-5]\d)$/.exec(trimmed);
    if (!m) {
      await dialogs.alert({ title: "형식 오류", message: "HH:MM으로 입력하세요 (예: 09:30, 14:00)" });
      return;
    }
    updateCard.mutate({
      cardId: c.id,
      allDay: false,
      startTime: `${m[1].padStart(2, "0")}:${m[2]}`,
    });
  }

  async function onToolDelete() {
    const sel = [...selectedIds];
    if (sel.length === 0) return;
    const ok = await dialogs.confirm({
      title: "카드 삭제",
      message: `선택한 ${sel.length}개 카드를 삭제할까요?`,
      confirmLabel: "삭제",
      danger: true,
    });
    if (!ok) return;
    for (const cardId of sel) deleteCard.mutate(cardId);
    setSelectedIds(new Set());
  }

  async function onDeleteRelation(id: string) {
    const ok = await dialogs.confirm({ title: "연결 삭제", confirmLabel: "삭제", danger: true });
    if (ok) deleteRelation.mutate(id);
  }

  /** 엣지 라벨 편집 — 라벨(summary)이 곧 화살표가 나타내는 관계. 비우면 라벨 제거. */
  async function onEditEdgeLabel(rel: CardRelation) {
    const v = await dialogs.prompt({
      title: "엣지 라벨",
      message: "이 화살표가 나타내는 관계 (비우면 제거)",
      defaultValue: rel.summary ?? "",
    });
    if (v === null) return;
    updateRelation.mutate({ relationId: rel.id, summary: v.trim() ? v.trim() : null });
  }

  async function onDeleteGroup(g: Group) {
    const ok = await dialogs.confirm({
      title: "그룹 삭제",
      message: `"${g.name}" 그룹을 삭제할까요? (카드는 유지됩니다)`,
      confirmLabel: "삭제",
      danger: true,
    });
    if (ok) deleteGroup.mutate(g.id);
  }

  async function onDeleteGroupRelation(id: string) {
    const ok = await dialogs.confirm({
      title: "그룹 연결 삭제",
      confirmLabel: "삭제",
      danger: true,
    });
    if (ok) deleteGroupRelation.mutate(id);
  }

  /** 그룹 엣지 라벨 편집 — 라벨(summary)이 곧 화살표가 나타내는 관계. 비우면 제거. */
  async function onEditGroupEdgeLabel(rel: GroupRelation) {
    const v = await dialogs.prompt({
      title: "그룹 엣지 라벨",
      message: "이 화살표가 나타내는 관계 (비우면 제거)",
      defaultValue: rel.summary ?? "",
    });
    if (v === null) return;
    updateGroupRelation.mutate({ relationId: rel.id, summary: v.trim() ? v.trim() : null });
  }

  // 새 카드 확정 — 생성 후 분류 선택 시 지정(일자 비우면 미정/백로그).
  async function onCreateCard(input: {
    title: string;
    startDate: string | null;
    categoryId: string | null;
  }) {
    const card = await createCard.mutateAsync({
      title: input.title,
      startDate: input.startDate,
    });
    if (input.categoryId) {
      setCardCategory.mutate({ cardId: card.id, categoryId: input.categoryId });
    }
    closeNewCard();
  }

  return {
    reportError,
    onEditTitle,
    onSetTime,
    onToolDelete,
    onDeleteRelation,
    onEditEdgeLabel,
    onDeleteGroup,
    onDeleteGroupRelation,
    onEditGroupEdgeLabel,
    onCreateCard,
  };
}
