import { lazy, Suspense, useEffect, useState } from "react";
import { api } from "../../lib/api";
import type { DocKind } from "../../lib/route";

// 본문(마크다운+KaTeX)은 무겁고 항상 쓰진 않으므로 지연 로드 — 초기 번들에서 분리.
const CardBody = lazy(() => import("../doc/CardBody").then((m) => ({ default: m.CardBody })));
const GroupBody = lazy(() => import("../doc/GroupBody").then((m) => ({ default: m.GroupBody })));
const TabBody = lazy(() => import("../doc/TabBody").then((m) => ({ default: m.TabBody })));
const SubjectBody = lazy(() =>
  import("../doc/SubjectBody").then((m) => ({ default: m.SubjectBody })),
);

/** 문서 종류 접두 글리프 — doctab 시절 어휘 유지(카드는 접두 없음). */
const PREFIX: Partial<Record<DocKind, string>> = { group: "▣ ", tab: "▭ ", subject: "◈ " };

function isTypingTarget(t: EventTarget | null): boolean {
  const el = t as HTMLElement | null;
  return !!el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA" || el.isContentEditable);
}

/**
 * 우측 문서 패널 — doctab 대체(단순화 A안). URL의 doc 하나만 열리며 ✕·Esc로 닫는다(닫기=doc null).
 * "열린 문서 목록"이라는 관리 대상 자체를 없애 화면 모델을 트리(구조)·캔버스(공간)·패널(상세)
 * 3역할로 고정한다. 카드·그룹 제목은 단건 조회로 헤더에 표시한다.
 */
export function DocPanel({
  kind,
  id,
  tabId,
  subjectName,
  tabName,
  onClose,
}: {
  kind: DocKind;
  id: string;
  /** 카드·그룹 본문이 참조할 활성 탭 id. */
  tabId: string;
  subjectName: string;
  tabName: string | null;
  onClose: () => void;
}) {
  const [fetchedTitle, setFetchedTitle] = useState<string | null>(null);
  useEffect(() => {
    if (kind !== "card" && kind !== "group") return;
    let alive = true;
    api
      .get<{ title?: string; name?: string }>(
        kind === "card" ? `/api/cards/${id}` : `/api/groups/${id}`,
      )
      .then(
        (d) => {
          if (alive) setFetchedTitle(d.title ?? d.name ?? "");
        },
        () => {
          // 부재/권한 없음 — 본문 영역이 에러를 표시하므로 제목은 빈 채 둔다.
        },
      );
    return () => {
      alive = false;
    };
  }, [kind, id]);

  // Esc=닫기 — 캔버스 단축키(Esc=선택 해제)보다 먼저 capture 단계에서 소비. 입력 중엔 무시.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key !== "Escape" || isTypingTarget(e.target)) return;
      e.stopPropagation();
      onClose();
    }
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [onClose]);

  const title =
    kind === "subject" ? subjectName : kind === "tab" ? (tabName ?? "") : (fetchedTitle ?? "");

  return (
    <aside className="docp" aria-label="문서 패널">
      <header className="docp-head">
        <span className="docp-title" title={title}>
          {PREFIX[kind]}
          {title || "(제목 없음)"}
        </span>
        <button
          type="button"
          className="docp-x"
          onClick={onClose}
          title="닫기 (Esc)"
          aria-label="문서 닫기"
        >
          ✕
        </button>
      </header>
      <div className="docp-body">
        <Suspense fallback={<div className="dw-empty">불러오는 중...</div>}>
          {kind === "card" ? (
            <CardBody cardId={id} tabId={tabId} />
          ) : kind === "group" ? (
            <GroupBody groupId={id} tabId={tabId} />
          ) : kind === "tab" ? (
            <TabBody tabId={id} />
          ) : kind === "subject" ? (
            <SubjectBody subjectId={id} />
          ) : null}
        </Suspense>
      </div>
    </aside>
  );
}
