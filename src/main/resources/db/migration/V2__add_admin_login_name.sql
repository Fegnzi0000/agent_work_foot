-- 管理员网页使用独立账号名登录；普通小程序用户仍只使用邮箱登录。
-- NULL 表示该用户不是可通过管理员入口登录的账号。
ALTER TABLE users
    ADD COLUMN admin_login_name VARCHAR(32) NULL AFTER email,
    ADD UNIQUE KEY uk_users_admin_login_name (admin_login_name);
