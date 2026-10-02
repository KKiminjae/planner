-- 1인용 앱의 기존 데이터는 기본 소유자(1)에 귀속합니다.
ALTER TABLE categories ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE photos ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX ix_categories_owner ON categories (owner_id, deleted_at, display_order);
CREATE INDEX ix_photos_owner ON photos (owner_id, record_id);
