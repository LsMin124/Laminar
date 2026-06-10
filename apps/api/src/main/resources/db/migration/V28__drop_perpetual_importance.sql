-- V28: F6 — importance 'perpetual-ver' 화석 제거.
-- perpetual(영구노트) 기능은 V17에서 전면 폐기됐고(linked_perpetual_id 컬럼·FK·biconditional
-- CHECK는 당시 CASCADE로 정리) chk_cards_importance 허용값과 자바 enum에만 값이 남아 있었다.
-- 잔존 행을 normal로 정리(멱등)한 뒤 CHECK를 6종으로 재생성한다.
-- 자바 CardImportance.PERPETUAL_VER도 본 마이그레이션과 동시 제거(같은 커밋).
UPDATE cards SET importance = 'normal' WHERE importance = 'perpetual-ver';

ALTER TABLE cards DROP CONSTRAINT chk_cards_importance;
ALTER TABLE cards ADD CONSTRAINT chk_cards_importance CHECK (
    importance IN ('normal', 'cf', 'urgent', 'purchase', 'article', 'process')
);
