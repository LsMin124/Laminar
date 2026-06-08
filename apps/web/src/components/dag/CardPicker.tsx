import { useState } from "react";
import { useCardById, useTabGraph, useTabs } from "../../lib/dag";

/**
 * 예약 폼 카드 연결 picker — 탭 선택 → 그 탭의 카드 선택.
 * 장비/예약은 주제 단위, 카드는 탭 단위이므로 탭을 먼저 고르는 2단 구조로 크로스레벨을 해소한다.
 */
export function CardPicker({
  value,
  onChange,
}: {
  value: string | null;
  onChange: (id: string | null) => void;
}) {
  const tabs = useTabs();
  const [tabId, setTabId] = useState<string | null>(null);
  const graph = useTabGraph(tabId);
  const cards = graph.data?.cards ?? [];

  return (
    <div className="cardpick">
      <select
        className="eq-input"
        value={tabId ?? ""}
        onChange={(e) => {
          setTabId(e.target.value || null);
          onChange(null);
        }}
      >
        <option value="">탭 선택</option>
        {(tabs.data ?? []).map((t) => (
          <option key={t.id} value={t.id}>
            {t.name}
          </option>
        ))}
      </select>
      <select
        className="eq-input"
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value || null)}
        disabled={!tabId}
      >
        <option value="">{tabId ? "연결 안 함" : "탭 먼저 선택"}</option>
        {cards.map((c) => (
          <option key={c.id} value={c.id}>
            {c.title || "(제목 없음)"}
          </option>
        ))}
      </select>
    </div>
  );
}

/** 연결된 카드 칩 — cardId→제목 해석(없거나 404면 일반 '카드' 표시). */
export function LinkedCardChip({ cardId }: { cardId: string }) {
  const card = useCardById(cardId);
  const title = card.data?.title;
  return (
    <span className="eq-cardchip" title={title ? `연결 카드: ${title}` : "연결 카드"}>
      🔗 {card.isLoading ? "…" : title || "카드"}
    </span>
  );
}
