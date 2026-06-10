import { useGroupById, useUpdateGroup } from "../../lib/dag";
import { MarkdownDoc } from "./MarkdownDoc";

/**
 * 그룹 본문 — 공유 MarkdownDoc 셸에 그룹 데이터(이름·bodyMd)와 저장 콜백을 주입.
 * 전체 bodyMd는 그래프 페이로드에서 빠졌으므로 단건 조회(useGroupById)로 가져온다(카드와 동형).
 * 편집/미리보기/자동저장 로직은 MarkdownDoc이 담당(카드 본문과 공유).
 */
export function GroupBody({ groupId, tabId }: { groupId: string; tabId: string }) {
  const group = useGroupById(groupId);
  const updateGroup = useUpdateGroup(tabId);
  return (
    <MarkdownDoc
      title={group.data?.name || "(이름 없음)"}
      value={group.data?.bodyMd ?? null}
      loading={group.isLoading}
      missing={!group.data && !group.isLoading}
      missingLabel="그룹을 찾을 수 없습니다."
      placeholder="그룹 본문 — 이 그룹(목표·카드 묶음)에 대한 메모를 마크다운으로"
      onSave={(md) => updateGroup.mutate({ groupId, bodyMd: md })}
    />
  );
}
