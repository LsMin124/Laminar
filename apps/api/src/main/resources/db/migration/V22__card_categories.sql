-- 카드 카테고리 — 주제(subject) 단위로 공유되는 명명 카테고리(이름 + 색). 카드 좌측 스트라이프 색에 반영.
CREATE TABLE card_categories (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id  UUID        NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    name        TEXT        NOT NULL CHECK (char_length(name) <= 100),
    color       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_card_categories_subject ON card_categories(subject_id);

CREATE TRIGGER card_categories_updated_at
    BEFORE UPDATE ON card_categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 카드 → 카테고리(선택). 카테고리 삭제 시 카드의 분류만 해제(SET NULL).
ALTER TABLE cards
    ADD COLUMN category_id UUID REFERENCES card_categories(id) ON DELETE SET NULL;
