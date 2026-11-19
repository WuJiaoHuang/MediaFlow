-- Additive migration: no user/media rows are deleted or recreated.
USE mediaflow;
SET @scope_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='media_asset' AND column_name='access_scope'),'SELECT 1','ALTER TABLE media_asset ADD COLUMN access_scope VARCHAR(12) NOT NULL DEFAULT ''LIBRARY''');
PREPARE scope_statement FROM @scope_ddl;
EXECUTE scope_statement;
DEALLOCATE PREPARE scope_statement;

CREATE TABLE IF NOT EXISTS editing_task (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, title VARCHAR(200) NOT NULL, description VARCHAR(4000) NOT NULL,
 created_by BIGINT NOT NULL, assignee_id BIGINT NOT NULL, reviewer_id BIGINT NOT NULL,
 priority VARCHAR(10) NOT NULL, due_at DATETIME NULL, status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, version INT NOT NULL DEFAULT 0,
 CONSTRAINT fk_task_creator FOREIGN KEY(created_by) REFERENCES sys_user(id) ON DELETE RESTRICT,
 CONSTRAINT fk_task_assignee FOREIGN KEY(assignee_id) REFERENCES sys_user(id) ON DELETE RESTRICT,
 CONSTRAINT fk_task_reviewer FOREIGN KEY(reviewer_id) REFERENCES sys_user(id) ON DELETE RESTRICT,
 CONSTRAINT ck_task_status CHECK(status IN ('PENDING','PROCESSING','REVIEWING','COMPLETED')),
 CONSTRAINT ck_task_priority CHECK(priority IN ('LOW','NORMAL','HIGH','URGENT')),
 INDEX idx_task_assignee_status(assignee_id,status,created_at,id),
 INDEX idx_task_reviewer_status(reviewer_id,status,created_at,id),
 INDEX idx_task_status_priority(status,priority,created_at,id),
 INDEX idx_task_due(due_at,id)
);
CREATE TABLE IF NOT EXISTS editing_task_asset (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,task_id BIGINT NOT NULL,asset_id BIGINT NOT NULL,
 UNIQUE KEY uk_task_asset(task_id,asset_id),
 CONSTRAINT fk_task_asset_task FOREIGN KEY(task_id) REFERENCES editing_task(id) ON DELETE RESTRICT,
 CONSTRAINT fk_task_asset_asset FOREIGN KEY(asset_id) REFERENCES media_asset(id) ON DELETE RESTRICT,
 INDEX idx_task_asset_asset(asset_id,task_id)
);
CREATE TABLE IF NOT EXISTS editing_task_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,task_id BIGINT NOT NULL,operator_id BIGINT NOT NULL,
 operator_name VARCHAR(100) NOT NULL,operation_type VARCHAR(30) NOT NULL,field_name VARCHAR(40) NOT NULL,
 old_value TEXT NULL,new_value TEXT NULL,reason VARCHAR(2000) NULL,created_at DATETIME NOT NULL,
 CONSTRAINT fk_task_log_task FOREIGN KEY(task_id) REFERENCES editing_task(id) ON DELETE RESTRICT,
 CONSTRAINT fk_task_log_operator FOREIGN KEY(operator_id) REFERENCES sys_user(id) ON DELETE RESTRICT,
 INDEX idx_task_log_time(task_id,created_at,id),
 INDEX idx_task_log_operator(operator_id,created_at)
);
