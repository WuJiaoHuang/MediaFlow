CREATE TABLE IF NOT EXISTS media_category (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, category_name VARCHAR(100) NOT NULL UNIQUE, parent_id BIGINT NULL,
 sort_order INT NOT NULL DEFAULT 0, status TINYINT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 CONSTRAINT fk_category_parent FOREIGN KEY(parent_id) REFERENCES media_category(id) ON DELETE RESTRICT,
 CONSTRAINT ck_category_status CHECK(status IN (0,1))
);
CREATE TABLE IF NOT EXISTS media_file (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, original_filename VARCHAR(255) NOT NULL, storage_key VARCHAR(80) NOT NULL UNIQUE,
 mime_type VARCHAR(100) NOT NULL, file_size BIGINT NOT NULL, uploaded_by BIGINT NOT NULL, created_at DATETIME NOT NULL,
 CONSTRAINT fk_file_uploader FOREIGN KEY(uploaded_by) REFERENCES sys_user(id) ON DELETE RESTRICT,
 CONSTRAINT ck_file_size CHECK(file_size > 0)
);
CREATE TABLE IF NOT EXISTS media_asset (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, title VARCHAR(200) NOT NULL, description VARCHAR(2000) NOT NULL,
 file_id BIGINT NOT NULL UNIQUE, category_id BIGINT NULL, media_type VARCHAR(10) NOT NULL,
 created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE', version INT NOT NULL DEFAULT 0, upload_key VARCHAR(64) NOT NULL, access_scope VARCHAR(12) NOT NULL DEFAULT 'LIBRARY',
 CONSTRAINT uk_asset_upload UNIQUE(created_by,upload_key),
 CONSTRAINT fk_asset_file FOREIGN KEY(file_id) REFERENCES media_file(id) ON DELETE RESTRICT,
 CONSTRAINT fk_asset_category FOREIGN KEY(category_id) REFERENCES media_category(id) ON DELETE RESTRICT,
 CONSTRAINT fk_asset_creator FOREIGN KEY(created_by) REFERENCES sys_user(id) ON DELETE RESTRICT,
 CONSTRAINT fk_asset_updater FOREIGN KEY(updated_by) REFERENCES sys_user(id) ON DELETE RESTRICT,
 CONSTRAINT ck_asset_type CHECK(media_type IN ('IMAGE','AUDIO','VIDEO')),
 CONSTRAINT ck_asset_status CHECK(status IN ('ACTIVE','ARCHIVED'))
);
CREATE TABLE IF NOT EXISTS media_change_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, asset_id BIGINT NOT NULL, operator_id BIGINT NOT NULL,
 operator_name VARCHAR(100) NOT NULL, asset_title VARCHAR(200) NOT NULL, operation_type VARCHAR(12) NOT NULL,
 field_name VARCHAR(50) NOT NULL, old_value TEXT NULL, new_value TEXT NULL, old_label VARCHAR(200) NULL,
 new_label VARCHAR(200) NULL, created_at DATETIME NOT NULL,
 CONSTRAINT fk_log_asset FOREIGN KEY(asset_id) REFERENCES media_asset(id) ON DELETE RESTRICT,
 CONSTRAINT fk_log_operator FOREIGN KEY(operator_id) REFERENCES sys_user(id) ON DELETE RESTRICT
);
