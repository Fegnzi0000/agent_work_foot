-- 管理员产品语义收敛为USER/ADMIN两级：保留RBAC历史表，不再保留SUPER_ADMIN账号能力。
-- 先撤销原SUPER_ADMIN全部会话和临时凭据，再降级角色并递增安全版本，使旧Access Token立即失效。
UPDATE refresh_tokens rt
JOIN users u ON u.id = rt.user_id
JOIN roles r ON r.id = u.role_id
SET rt.revoked_at = UTC_TIMESTAMP(3),
    rt.revoke_reason = 'ROLE_CHANGED'
WHERE r.code = 'SUPER_ADMIN'
  AND rt.revoked_at IS NULL;

UPDATE temporary_passwords tp
JOIN users u ON u.id = tp.user_id
JOIN roles r ON r.id = u.role_id
SET tp.revoked_at = UTC_TIMESTAMP(3)
WHERE r.code = 'SUPER_ADMIN'
  AND tp.used_at IS NULL
  AND tp.revoked_at IS NULL;

UPDATE users u
JOIN roles previous_role ON previous_role.id = u.role_id
JOIN roles admin_role ON admin_role.code = 'ADMIN' AND admin_role.is_active = 1
SET u.role_id = admin_role.id,
    u.auth_version = u.auth_version + 1
WHERE previous_role.code = 'SUPER_ADMIN';

UPDATE roles
SET is_active = 0
WHERE code = 'SUPER_ADMIN';
