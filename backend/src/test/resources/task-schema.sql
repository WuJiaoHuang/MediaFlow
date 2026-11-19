CREATE TABLE IF NOT EXISTS editing_task (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, title VARCHAR(200) NOT NULL, description VARCHAR(4000) NOT NULL,
 created_by BIGINT NOT NULL, assignee_id BIGINT NOT NULL, reviewer_id BIGINT NOT NULL,
 priority VARCHAR(10) NOT NULL, due_at DATETIME NULL, status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, version INT NOT NULL DEFAULT 0,
 CONSTRAINT fk_task_creator FOREIGN KEY(created_by) REFERENCES sys_user(id) ON DELETE RESTRICT,
 CONSTRAINT fk_task_assignee FOREIGN KEY(assignee_id) REFERENCES sys_user(id) ON DELETE RESTRICT,
 CONSTRAINT fk_task_reviewer FOREIGN KEY(reviewer_id) REFERENCES sys_user(id) ON DELETE RESTRICT,
 CONSTRAINT ck_task_status CHECK(status IN ('PENDING','PROCESSING','REVIEWING','COMPLETED')),
 CONSTRAINT ck_task_priority CHECK(priority IN ('LOW','NORMAL','HIGH','URGENT'))
);
CREATE TABLE IF NOT EXISTS editing_task_asset (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,task_id BIGINT NOT NULL,asset_id BIGINT NOT NULL,
 CONSTRAINT uk_task_asset UNIQUE(task_id,asset_id),
 CONSTRAINT fk_task_asset_task FOREIGN KEY(task_id) REFERENCES editing_task(id) ON DELETE RESTRICT,
 CONSTRAINT fk_task_asset_asset FOREIGN KEY(asset_id) REFERENCES media_asset(id) ON DELETE RESTRICT
);
CREATE TABLE IF NOT EXISTS editing_task_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,task_id BIGINT NOT NULL,operator_id BIGINT NOT NULL,
 operator_name VARCHAR(100) NOT NULL,operation_type VARCHAR(30) NOT NULL,field_name VARCHAR(40) NOT NULL,
 old_value TEXT NULL,new_value TEXT NULL,reason VARCHAR(2000) NULL,created_at DATETIME NOT NULL,
 CONSTRAINT fk_task_log_task FOREIGN KEY(task_id) REFERENCES editing_task(id) ON DELETE RESTRICT,
 CONSTRAINT fk_task_log_operator FOREIGN KEY(operator_id) REFERENCES sys_user(id) ON DELETE RESTRICT
);
