-- DEVELOPMENT ONLY. Do not load these known local test accounts in production.
-- Re-running seeds does not overwrite passwords, statuses or existing role assignments.
USE mediaflow;
START TRANSACTION;
INSERT INTO sys_role(id,role_code,role_name,description) VALUES
(1,'ADMIN','管理员','账号与权限管理'),(2,'EDITOR','剪辑人员','剪辑任务参与者'),(3,'REVIEWER','审核人员','授权作品审核者')
ON DUPLICATE KEY UPDATE role_code=role_code;
INSERT IGNORE INTO sys_user(username,password_hash,display_name,status) VALUES('admin','$2b$12$/djav/mC49x8j1b5ezFWBOj6lX8.3/aNK.FaFOUFO3v.eud2U9/vq','本地管理员',1);
INSERT IGNORE INTO sys_user_role(user_id,role_id) SELECT u.id,r.id FROM sys_user u JOIN sys_role r ON r.role_code='ADMIN' WHERE u.username='admin' AND NOT EXISTS(SELECT 1 FROM sys_user_role ur WHERE ur.user_id=u.id);
INSERT IGNORE INTO sys_user(username,password_hash,display_name,status) VALUES('editor01','$2b$12$fA5pBcncOeaRWX49K5An4eDqM9YPmx7UMBXBfyRoyhdef3sUzQbMO','剪辑人员',1);
INSERT IGNORE INTO sys_user_role(user_id,role_id) SELECT u.id,r.id FROM sys_user u JOIN sys_role r ON r.role_code='EDITOR' WHERE u.username='editor01' AND NOT EXISTS(SELECT 1 FROM sys_user_role ur WHERE ur.user_id=u.id);
INSERT IGNORE INTO sys_user(username,password_hash,display_name,status) VALUES('reviewer01','$2b$12$LJ2zXZVvMl2qMcF/BoS2/eL2P5wWQmc1kAitw0X8CRwWmMX1Y1Nti','审核人员',1);
INSERT IGNORE INTO sys_user_role(user_id,role_id) SELECT u.id,r.id FROM sys_user u JOIN sys_role r ON r.role_code='REVIEWER' WHERE u.username='reviewer01' AND NOT EXISTS(SELECT 1 FROM sys_user_role ur WHERE ur.user_id=u.id);
COMMIT;
