import { useCardById, useUpdateCard } from "../../lib/dag";
import { MarkdownDoc } from "./MarkdownDoc";

/**
 * 카드 본문 — 공유 MarkdownDoc 셸에 카드 데이터(제목·bodyMd)와 저장 콜백을 주입.
 * 전체 bodyMd는 그래프 페이로드에서 빠졌으므로 단건 조회(useCardById)로 가져온다.
 * 편집/미리보기/자동저장 로직은 MarkdownDoc이 담당(그룹 본문과 공유).
 */
export function CardBody({ cardId, tabId }: { cardId: string; tabId: string }) {
  const card = useCardById(cardId);
  const updateCard = useUpdateCard(tabId);
  return (
    <MarkdownDoc
      title={card.data?.title || "(제목 없음)"}
      value={card.data?.bodyMd ?? null}
      loading={card.isLoading}
      missing={!card.data && !card.isLoading}
      missingLabel="카드를 찾을 수 없습니다."
      placeholder="마크다운 본문 — 이미지 ![alt](url), 수식 인라인 $E=mc^2$ / 블록 $$ ... $$"
      onSave={(md) => updateCard.mutate({ cardId, bodyMd: md })}
    />
  );
}
