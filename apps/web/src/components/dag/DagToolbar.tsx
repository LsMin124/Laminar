import { CategoryBar } from "./CategoryBar";
import { GroupBar } from "./GroupBar";
import type { Card, Category, Group } from "../../lib/graphTypes";

/**
 * DAG 캔버스 상단 툴바 — 선택 상태에 따라 작용하는 액션 버튼 모음(선행조건 미충족 시 disabled).
 * 상태·핸들러는 컨테이너(DagCanvas)가 보유하고 props로 주입하는 프레젠테이셔널 컴포넌트.
 */
export function DagToolbar({
  tabId,
  categories,
  cardCategoryIds,
  groups,
  groupMembers,
  selectedCards,
  sole,
  soleInGroup,
  selCount,
  hideCompleted,
  setHideCompleted,
  linkSource,
  groupLinkSource,
  onAddCard,
  onToolLink,
  onSetTime,
  onEditTitle,
  onOpenCard,
  onUngroup,
  onToolDelete,
  scrollToToday,
  setLinkSource,
  setGroupLinkSource,
}: {
  tabId: string;
  categories: Category[];
  cardCategoryIds: Record<string, string>;
  groups: Group[];
  groupMembers: Record<string, string[]>;
  selectedCards: Card[];
  /** 단일 선택 카드(0개·2+개면 null) — 단일 대상 액션 활성 판정. */
  sole: Card | null;
  soleInGroup: boolean;
  selCount: number;
  hideCompleted: boolean;
  setHideCompleted: (v: boolean) => void;
  linkSource: string | null;
  groupLinkSource: string | null;
  onAddCard: () => void;
  onToolLink: () => void;
  onSetTime: (c: Card) => void;
  onEditTitle: (c: Card) => void;
  onOpenCard?: (cardId: string, title: string) => void;
  onUngroup: () => void;
  onToolDelete: () => void;
  scrollToToday: () => void;
  setLinkSource: (v: string | null) => void;
  setGroupLinkSource: (v: string | null) => void;
}) {
  return (
    <div className="dag-toolbar">
      <button type="button" className="dag-tool" onClick={onAddCard} title="새 카드">
        ＋ 카드
      </button>
      <span className="dag-tool-sep" />
      <button
        type="button"
        className="dag-tool"
        disabled={!sole}
        onClick={onToolLink}
        title="선택 카드에서 연결 시작 (1개 선택)"
      >
        ⇢ 연결
      </button>
      <button
        type="button"
        className="dag-tool"
        disabled={!sole}
        onClick={() => {
          if (sole) onSetTime(sole);
        }}
        title="시간 설정 (1개 선택)"
      >
        ⏱ 시간
      </button>
      <button
        type="button"
        className="dag-tool"
        disabled={!sole}
        onClick={() => {
          if (sole) onEditTitle(sole);
        }}
        title="제목 편집 (1개 선택)"
      >
        ✎ 제목
      </button>
      <button
        type="button"
        className="dag-tool"
        disabled={!sole}
        onClick={() => {
          if (sole) onOpenCard?.(sole.id, sole.title);
        }}
        title="본문 열기 (1개 선택 · 카드 더블클릭도 가능)"
      >
        ▤ 본문
      </button>
      <CategoryBar
        tabId={tabId}
        categories={categories}
        cards={selectedCards}
        cardCategoryIds={cardCategoryIds}
      />
      <span className="dag-tool-sep" />
      <GroupBar tabId={tabId} groups={groups} cards={selectedCards} groupMembers={groupMembers} />
      <button
        type="button"
        className="dag-tool"
        disabled={!soleInGroup}
        onClick={onUngroup}
        title="선택 카드를 모든 그룹에서 제외"
      >
        ⊟ 그룹 해제
      </button>
      <button
        type="button"
        className="dag-tool danger"
        disabled={selCount < 1}
        onClick={onToolDelete}
        title="선택 카드 삭제"
      >
        ✕ 삭제{selCount > 0 ? ` (${selCount})` : ""}
      </button>
      <label className="dag-toggle">
        <input
          type="checkbox"
          checked={hideCompleted}
          onChange={(e) => setHideCompleted(e.target.checked)}
        />
        완료 숨기기
      </label>
      <button
        type="button"
        className="dag-tool"
        onClick={scrollToToday}
        title="현재 날짜·상단으로 스크롤 복귀"
      >
        ⌖ 오늘로
      </button>
      <span className="dag-hint">
        {linkSource ? (
          <>
            <strong>연결 대상 카드 클릭</strong>{" "}
            <button type="button" className="dag-tool" onClick={() => setLinkSource(null)}>
              취소
            </button>
          </>
        ) : groupLinkSource ? (
          <>
            <strong>연결 대상 그룹의 ⇢ 클릭</strong>{" "}
            <button type="button" className="dag-tool" onClick={() => setGroupLinkSource(null)}>
              취소
            </button>
          </>
        ) : selCount > 0 ? (
          `${selCount}개 선택됨 · Shift+클릭 다중 · 빈 곳 클릭 해제`
        ) : (
          "카드 클릭=선택 · 빈 곳 더블클릭=새 카드 · 드래그=이동/기간"
        )}
      </span>
    </div>
  );
}
