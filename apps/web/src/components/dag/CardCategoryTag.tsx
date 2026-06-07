import { useState } from "react";
import { useCreateCategory, useSetCardCategory, type Category } from "../../lib/dag";
import { useDialogs } from "../ui/DialogProvider";

// 새 분류 기본색 — 웜·건조 톤. (관리 모달[툴바 🏷]에서 색 변경 가능)
const PALETTE = [
  "#d97757",
  "#c2693f",
  "#b0855f",
  "#a8534f",
  "#7d8471",
  "#6a8294",
  "#8a6d8a",
  "#7a7d52",
];

/** 분류 색(hex)에 대비되는 글자색 — 밝으면 어둡게, 어두우면 밝게. */
function readableText(hex: string): string {
  const h = hex.replace("#", "");
  const n =
    h.length === 3
      ? h
          .split("")
          .map((c) => c + c)
          .join("")
      : h;
  const r = parseInt(n.slice(0, 2), 16);
  const g = parseInt(n.slice(2, 4), 16);
  const b = parseInt(n.slice(4, 6), 16);
  return (0.299 * r + 0.587 * g + 0.114 * b) / 255 > 0.6 ? "#1a1208" : "#f5efe9";
}

/**
 * 카드 분류 태그 — 색+이름 단색 칩이자 분류 선택 드롭다운(얇은 스트라이프보다 잘 보이게).
 * 분류가 있으면 단색 태그, 없으면 옅은 '분류' 고스트(카드 호버 시 노출, CSS). 카드 드래그와
 * 충돌하지 않도록 포인터 이벤트는 stopPropagation.
 */
export function CardCategoryTag({
  tabId,
  cardId,
  categoryId,
  categories,
}: {
  tabId: string;
  cardId: string;
  categoryId: string | null;
  categories: Category[];
}) {
  const dialogs = useDialogs();
  const setCardCategory = useSetCardCategory(tabId);
  const createCategory = useCreateCategory();
  const [open, setOpen] = useState(false);

  const current = categoryId ? (categories.find((c) => c.id === categoryId) ?? null) : null;
  const color = current?.color ?? null;

  function assign(id: string | null) {
    setCardCategory.mutate({ cardId, categoryId: id });
    setOpen(false);
  }

  async function onNew() {
    const name = await dialogs.prompt({ title: "새 분류", placeholder: "분류 이름" });
    if (!name || !name.trim()) return;
    const c = PALETTE[categories.length % PALETTE.length];
    const created = await createCategory.mutateAsync({ name: name.trim(), color: c });
    assign(created.id);
  }

  return (
    <span className="cardtag-wrap" onPointerDown={(e) => e.stopPropagation()}>
      <button
        type="button"
        className={`cardtag${current ? "" : " empty"}`}
        style={
          color ? { background: color, color: readableText(color), borderColor: color } : undefined
        }
        onClick={(e) => {
          e.stopPropagation();
          setOpen((o) => !o);
        }}
        title={current ? `분류: ${current.name}` : "분류 지정"}
      >
        {current ? current.name : "미분류"}
      </button>
      {open && (
        <>
          <div
            className="cardtag-backdrop"
            onPointerDown={(e) => {
              e.stopPropagation();
              setOpen(false);
            }}
          />
          <div className="cardtag-menu" onPointerDown={(e) => e.stopPropagation()}>
            <button
              type="button"
              className={`cardtag-item${!categoryId ? " active" : ""}`}
              onClick={() => assign(null)}
            >
              <span className="cardtag-sw none" />
              분류 없음
            </button>
            {categories.map((cat) => (
              <button
                key={cat.id}
                type="button"
                className={`cardtag-item${categoryId === cat.id ? " active" : ""}`}
                onClick={() => assign(cat.id)}
              >
                <span className="cardtag-sw" style={{ background: cat.color ?? "#888" }} />
                {cat.name}
              </button>
            ))}
            <button type="button" className="cardtag-item add" onClick={onNew}>
              ＋ 새 분류
            </button>
          </div>
        </>
      )}
    </span>
  );
}
