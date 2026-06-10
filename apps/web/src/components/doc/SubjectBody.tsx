import { useSubjects, useUpdateSubject } from "../../lib/dag";
import { MarkdownDoc } from "./MarkdownDoc";

/**
 * 주제(워크스페이스) 본문 — 공유 MarkdownDoc 셸에 주제 데이터(이름·bodyMd)와 저장 콜백을 주입.
 * 본문 문서는 항상 활성 주제(=subjectId)에 대해 열리므로 PATCH /current로 저장한다(낙관적 캐시는 id로 타깃).
 */
export function SubjectBody({ subjectId }: { subjectId: string }) {
  const subjects = useSubjects();
  const updateSubject = useUpdateSubject();
  const subject = subjects.data?.find((s) => s.id === subjectId);
  return (
    <MarkdownDoc
      title={subject?.name ?? "(주제)"}
      value={subject?.bodyMd ?? null}
      loading={subjects.isLoading}
      missing={!subject && !subjects.isLoading}
      missingLabel="주제를 찾을 수 없습니다."
      placeholder="주제 본문 — 이 연구 주제(워크스페이스) 전반의 개요·메모를 마크다운으로"
      onSave={(md) => updateSubject.mutate({ id: subjectId, bodyMd: md })}
    />
  );
}
