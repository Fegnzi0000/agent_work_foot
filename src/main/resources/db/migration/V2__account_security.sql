-- 账号安全版本用于使改密、注销和管理员安全操作后的旧 Access Token 立即失效。
ALTER TABLE users
    ADD COLUMN auth_version INT NOT NULL DEFAULT 0 AFTER must_change_password;
