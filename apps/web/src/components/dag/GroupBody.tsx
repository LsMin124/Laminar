import { useTabGraph, useUpdateGroup } from "../../lib/dag";
import { MarkdownDoc } from "./MarkdownDoc";

/**
 * 그룹 본문 — 공유 MarkdownDoc 셸에 그룹 데이터(이름·bodyMd)와 저장 콜백을 주입.
 * 카드 본문과 동일한 편집/미리보기/자동저장 동작(MarkdownDoc 공유).
 */
export function GroupBody({ groupId, tabId }: { groupId: string; tabId: string }) {
  const graph = useTabGraph(tabId);
  const updateGroup = useUpdateGroup(tabId);
  const group = graph.data?.groups.find((g) => g.id === groupId);
  return (
    <MarkdownDoc
      title={group?.name || "(이름 없음)"}
      value={group?.bodyMd ?? null}
      loading={graph.isLoading}
      missing={!group && !graph.isLoading}
      missingLabel="그룹을 찾을 수 없습니다."
      placeholder="그룹 본문 — 이 그룹(목표·카드 묶음)에 대한 메모를 마크다운으로"
      onSave={(md) => updateGroup.mutate({ groupId, bodyMd: md })}
    />
  );
}
