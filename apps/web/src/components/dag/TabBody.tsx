import { useTabs, useUpdateTab } from "../../lib/dag";
import { MarkdownDoc } from "./MarkdownDoc";

/**
 * 탭(보드) 본문 — 공유 MarkdownDoc 셸에 탭 데이터(이름·bodyMd)와 저장 콜백을 주입.
 * 카드/그룹 본문과 동일한 편집/미리보기/자동저장 동작.
 */
export function TabBody({ tabId }: { tabId: string }) {
  const tabs = useTabs();
  const updateTab = useUpdateTab();
  const tab = tabs.data?.find((t) => t.id === tabId);
  return (
    <MarkdownDoc
      title={tab?.name ?? "(탭)"}
      value={tab?.bodyMd ?? null}
      loading={tabs.isLoading}
      missing={!tab && !tabs.isLoading}
      missingLabel="탭을 찾을 수 없습니다."
      placeholder="탭 본문 — 이 탭(보드)의 개요·메모를 마크다운으로"
      onSave={(md) => updateTab.mutate({ tabId, bodyMd: md })}
    />
  );
}
