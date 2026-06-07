import { useRef, useState } from "react";
import {
  useCreateCategory,
  useDeleteCategory,
  useSetCardCategory,
  useUpdateCategory,
  type Card,
  type Category,
} from "../../lib/dag";
import { useDialogs } from "../ui/DialogProvider";
import "./CategoryBar.css";

// 새 분류 기본색 — 웜·건조 톤(둥근 캔디색 회피). 사용자는 관리 모달에서 색을 바꿀 수 있다.
const DEFAULT_PALETTE = [
  "#d97757",
  "#c2693f",
  "#b0855f",
  "#a8534f",
  "#7d8471",
  "#6a8294",
  "#8a6d8a",
  "#7a7d52",
];

/**
 * 카드 카테고리 툴바 컨트롤 — 선택 카드의 분류 지정(피커) + 분류 CRUD(관리 모달).
 * 카테고리 목록은 TabGraph(상위 DagCanvas)에서 props로 받고, 변경은 dag.ts 훅이 그래프를 무효화해 반영한다.
 */
export function CategoryBar({
  tabId,
  categories,
  cards,
  cardCategoryIds,
}: {
  tabId: string;
  categories: Category[];
  /** 선택된 카드들 — 0개면 지정 비활성(관리만), 1+개면 일괄 지정. */
  cards: Card[];
  /** cardId → categoryId (선택 카드들의 공통 분류 판정용). */
  cardCategoryIds: Record<string, string>;
}) {
  const dialogs = useDialogs();
  const setCardCategory = useSetCardCategory(tabId);
  const createCategory = useCreateCategory();
  const updateCategory = useUpdateCategory();
  const deleteCategory = useDeleteCategory();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [manageOpen, setManageOpen] = useState(false);
  // 색 라이브 프리뷰 + 디바운스 커밋(color input은 드래그 중 onChange가 연속 발화 → PATCH 폭주 방지).
  const [draftColors, setDraftColors] = useState<Record<string, string>>({});
  const colorTimers = useRef<Record<string, number>>({});

  // 선택 카드들의 공통 분류 — 모두 같으면 그 id, 섞였거나 비었으면 null(피커 현재 표시용).
  const sharedCategoryId = (() => {
    if (cards.length === 0) return null;
    const ids = new Set(cards.map((c) => cardCategoryIds[c.id] ?? ""));
    return ids.size === 1 ? [...ids][0] || null : null;
  })();

  function assign(categoryId: string | null) {
    if (cards.length === 0) return;
    for (const c of cards) setCardCategory.mutate({ cardId: c.id, categoryId });
    setPickerOpen(false);
  }

  async function onNewCategory(assignAfter: boolean) {
    const name = await dialogs.prompt({ title: "새 분류", placeholder: "분류 이름" });
    if (!name || !name.trim()) return;
    const color = DEFAULT_PALETTE[categories.length % DEFAULT_PALETTE.length];
    const created = await createCategory.mutateAsync({ name: name.trim(), color });
    if (assignAfter && cards.length > 0) {
      for (const c of cards) setCardCategory.mutate({ cardId: c.id, categoryId: created.id });
      setPickerOpen(false);
    }
  }

  async function onRename(cat: Category) {
    const name = await dialogs.prompt({ title: "분류 이름 변경", defaultValue: cat.name });
    if (!name || !name.trim() || name.trim() === cat.name) return;
    updateCategory.mutate({ id: cat.id, name: name.trim() });
  }

  function onColorChange(cat: Category, color: string) {
    setDraftColors((d) => ({ ...d, [cat.id]: color }));
    window.clearTimeout(colorTimers.current[cat.id]);
    colorTimers.current[cat.id] = window.setTimeout(() => {
      updateCategory.mutate({ id: cat.id, color });
    }, 300);
  }

  async function onDelete(cat: Category) {
    const ok = await dialogs.confirm({
      title: "분류 삭제",
      message: `"${cat.name}" 분류를 삭제할까요? 카드는 유지되고 분류만 해제됩니다.`,
      confirmLabel: "삭제",
      danger: true,
    });
    if (ok) deleteCategory.mutate(cat.id);
  }

  return (
    <span className="catbar">
      <button
        type="button"
        className="dag-tool"
        onClick={() => setPickerOpen((o) => !o)}
        title={
          cards.length > 0 ? "선택 카드 분류 지정 · 분류 관리" : "분류 관리 (카드 선택 시 지정 가능)"
        }
      >
        🏷 분류{cards.length > 1 ? ` (${cards.length})` : ""}
      </button>

      {pickerOpen && (
        <>
          <div className="catbar-backdrop" onClick={() => setPickerOpen(false)} />
          <div className="catbar-pop" role="menu">
            {cards.length > 0 && (
              <>
                <div className="catbar-sec">
                  {cards.length === 1 ? "이 카드 분류" : `${cards.length}개 카드 분류`}
                </div>
                <button
                  type="button"
                  className={`catbar-item${!sharedCategoryId ? " active" : ""}`}
                  onClick={() => assign(null)}
                >
                  <span className="catbar-sw none" />
                  분류 없음
                </button>
                {categories.map((cat) => (
                  <button
                    key={cat.id}
                    type="button"
                    className={`catbar-item${sharedCategoryId === cat.id ? " active" : ""}`}
                    onClick={() => assign(cat.id)}
                  >
                    <span className="catbar-sw" style={{ background: cat.color ?? "#888" }} />
                    {cat.name}
                  </button>
                ))}
                <button
                  type="button"
                  className="catbar-item add"
                  onClick={() => onNewCategory(true)}
                >
                  ＋ 새 분류 만들어 지정
                </button>
                <div className="catbar-divider" />
              </>
            )}
            <button
              type="button"
              className="catbar-item"
              onClick={() => {
                setManageOpen(true);
                setPickerOpen(false);
              }}
            >
              ⚙ 분류 관리…
            </button>
          </div>
        </>
      )}

      {manageOpen && (
        <div className="cat-overlay" onClick={() => setManageOpen(false)}>
          <div
            className="cat-modal"
            role="dialog"
            aria-label="분류 관리"
            onClick={(e) => e.stopPropagation()}
          >
            <header className="cat-head">
              <strong>분류 관리</strong>
              <button
                type="button"
                className="cat-x"
                onClick={() => setManageOpen(false)}
                aria-label="닫기"
              >
                ✕
              </button>
            </header>
            <ul className="cat-list">
              {categories.map((cat) => (
                <li key={cat.id} className="cat-row">
                  <input
                    type="color"
                    className="cat-color"
                    value={draftColors[cat.id] ?? cat.color ?? "#888888"}
                    onChange={(e) => onColorChange(cat, e.target.value)}
                    title="색 변경"
                  />
                  <button
                    type="button"
                    className="cat-name"
                    onClick={() => onRename(cat)}
                    title="이름 변경"
                  >
                    {cat.name}
                  </button>
                  <button type="button" className="cat-del danger" onClick={() => onDelete(cat)}>
                    삭제
                  </button>
                </li>
              ))}
              {categories.length === 0 && <li className="cat-empty">분류 없음</li>}
            </ul>
            <footer className="cat-foot">
              <button type="button" className="cat-create" onClick={() => onNewCategory(false)}>
                ＋ 새 분류
              </button>
            </footer>
          </div>
        </div>
      )}
    </span>
  );
}
