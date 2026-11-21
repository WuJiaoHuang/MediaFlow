-- Additive feedback migration; preserves all Stage 1-3 records.
USE mediaflow;
CREATE TABLE IF NOT EXISTS task_feedback (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, task_id BIGINT NOT NULL, asset_id BIGINT NULL,
 asset_title VARCHAR(200) NULL, media_type VARCHAR(10) NULL,
 creator_id BIGINT NOT NULL, creator_name VARCHAR(100) NOT NULL,
 type VARCHAR(8) NOT NULL, content VARCHAR(4000) NOT NULL, timecode_ms BIGINT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'OPEN', create_key VARCHAR(64) NOT NULL,
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, version INT NOT NULL DEFAULT 0,
 CONSTRAINT uk_feedback_create UNIQUE(task_id,creator_id,create_key),
 CONSTRAINT fk_feedback_task FOREIGN KEY(task_id) REFERENCES editing_task(id),
 CONSTRAINT fk_feedback_asset FOREIGN KEY(task_id,asset_id) REFERENCES editing_task_asset(task_id,asset_id),
 CONSTRAINT fk_feedback_creator FOREIGN KEY(creator_id) REFERENCES sys_user(id),
 CONSTRAINT ck_feedback_type CHECK(type IN ('TEXT','AUDIO','VIDEO')),
 CONSTRAINT ck_feedback_status CHECK(status IN ('OPEN','RESOLVED','CLOSED','REOPENED')),
 INDEX idx_feedback_task(task_id,created_at,id), INDEX idx_feedback_filter(task_id,status,type,asset_id),
 CONSTRAINT ck_feedback_time CHECK(timecode_ms IS NULL OR timecode_ms>=0)
);
CREATE TABLE IF NOT EXISTS task_feedback_reply (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, feedback_id BIGINT NOT NULL,
 creator_id BIGINT NOT NULL, creator_name VARCHAR(100) NOT NULL,
 content VARCHAR(4000) NOT NULL, created_at DATETIME NOT NULL,
 INDEX idx_feedback_reply(feedback_id,created_at,id),
 CONSTRAINT fk_feedback_reply FOREIGN KEY(feedback_id) REFERENCES task_feedback(id),
 CONSTRAINT fk_feedback_reply_user FOREIGN KEY(creator_id) REFERENCES sys_user(id)
);
CREATE TABLE IF NOT EXISTS task_feedback_attachment (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, feedback_id BIGINT NOT NULL,
 original_filename VARCHAR(255) NOT NULL, storage_key VARCHAR(100) NOT NULL,
 mime_type VARCHAR(80) NOT NULL, file_size BIGINT NOT NULL, uploaded_by BIGINT NOT NULL, created_at DATETIME NOT NULL,
 CONSTRAINT uk_feedback_attachment UNIQUE(feedback_id), CONSTRAINT uk_feedback_storage UNIQUE(storage_key),
 CONSTRAINT fk_feedback_attachment FOREIGN KEY(feedback_id) REFERENCES task_feedback(id),
 CONSTRAINT fk_feedback_upload_user FOREIGN KEY(uploaded_by) REFERENCES sys_user(id)
);
CREATE TABLE IF NOT EXISTS task_feedback_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, feedback_id BIGINT NOT NULL,
 operator_id BIGINT NOT NULL, operator_name VARCHAR(100) NOT NULL,
 operation_type VARCHAR(16) NOT NULL, old_status VARCHAR(12) NULL, new_status VARCHAR(12) NOT NULL,
 reason VARCHAR(2000) NULL, created_at DATETIME NOT NULL,
 INDEX idx_feedback_log(feedback_id,created_at,id),
 CONSTRAINT fk_feedback_log FOREIGN KEY(feedback_id) REFERENCES task_feedback(id),
 CONSTRAINT fk_feedback_log_user FOREIGN KEY(operator_id) REFERENCES sys_user(id)
);
