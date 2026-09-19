CREATE TABLE categories (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            name VARCHAR(50) NOT NULL,
                            color VARCHAR(20) NOT NULL,
                            display_order INT NOT NULL,
                            is_private BOOLEAN NOT NULL,
                            created_at DATETIME NOT NULL,
                            deleted_at DATETIME NULL
);

CREATE TABLE records (
                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                         category_id BIGINT NOT NULL,
                         record_date DATE NOT NULL,
                         record_time TIME NOT NULL,
                         memo TEXT NOT NULL,
                         image_url VARCHAR(500) NULL,
                         created_at DATETIME NOT NULL,
                         updated_at DATETIME NOT NULL,

                         CONSTRAINT fk_records_category
                             FOREIGN KEY (category_id)
                                 REFERENCES categories(id),

                         CONSTRAINT uq_records_category_date
                             UNIQUE (category_id, record_date)
);

CREATE TABLE completion_history (
                                    category_id BIGINT NOT NULL,
                                    record_date DATE NOT NULL,

                                    PRIMARY KEY (category_id, record_date),

                                    CONSTRAINT fk_completion_history_category
                                        FOREIGN KEY (category_id)
                                            REFERENCES categories(id)
);