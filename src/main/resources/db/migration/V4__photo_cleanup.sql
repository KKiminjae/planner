ALTER TABLE photos
    ADD COLUMN unlinked_at DATETIME NULL,
    ADD COLUMN delete_requested_at DATETIME NULL,
    ADD COLUMN delete_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN next_delete_attempt_at DATETIME NULL,
    ADD COLUMN deletion_token VARCHAR(36) NULL;

UPDATE photos SET unlinked_at = COALESCE(uploaded_at, created_at) WHERE record_id IS NULL;

CREATE INDEX ix_photos_cleanup ON photos (record_id, delete_requested_at, next_delete_attempt_at, unlinked_at);
CREATE INDEX ix_records_image_key ON records (image_key);
