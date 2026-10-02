CREATE TABLE photos (
    image_key VARCHAR(500) PRIMARY KEY,
    content_type VARCHAR(100) NULL,
    size_bytes BIGINT NULL,
    created_at DATETIME NOT NULL,
    uploaded_at DATETIME NULL,
    record_id BIGINT NULL,
    CONSTRAINT uq_photos_record UNIQUE (record_id),
    CONSTRAINT fk_photos_record FOREIGN KEY (record_id) REFERENCES records(id) ON DELETE SET NULL
);

-- 기존 사진 키가 있는 기록은 메타데이터에 연결을 보존합니다.
-- 기존 파일의 타입과 크기는 알 수 없으므로 NULL입니다.
INSERT INTO photos (image_key, created_at, uploaded_at, record_id)
SELECT image_key, created_at, created_at, id FROM records WHERE image_key IS NOT NULL;
