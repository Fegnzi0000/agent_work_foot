-- 管理员审计列表默认按时间翻页，并支持动作、结果及既有操作者/目标筛选。
ALTER TABLE admin_audit_logs ADD KEY idx_admin_audit_logs_created_at_id (created_at, id);
ALTER TABLE admin_audit_logs ADD KEY idx_admin_audit_logs_action_result_created_at (action, result, created_at, id);
