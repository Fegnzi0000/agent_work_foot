-- 管理员Dashboard仅做聚合读取；以下索引分别覆盖用户、饮食业务日/活跃日和转盘统计范围。
ALTER TABLE users ADD KEY idx_users_dashboard_role_status_created_at (role_id, status, created_at);
ALTER TABLE diet_records ADD KEY idx_diet_records_dashboard_deleted_business_source_user (deleted_at, business_date, source, user_id);
ALTER TABLE diet_records ADD KEY idx_diet_records_dashboard_deleted_created_user (deleted_at, created_at, user_id);
ALTER TABLE slot_spins ADD KEY idx_slot_spins_dashboard_created_status_user (created_at, status, user_id);
