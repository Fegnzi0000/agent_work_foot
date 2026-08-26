-- 当前开发基线：创建最终表结构、索引和必要种子数据。
-- 新环境只存在USER与ADMIN两级角色；历史升级逻辑不属于初始化数据。
CREATE TABLE roles (
    id CHAR(36) NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    is_system TINYINT(1) NOT NULL DEFAULT 1,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE permissions (
    id CHAR(36) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_permissions_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE role_permissions (
    role_id CHAR(36) NOT NULL,
    permission_id CHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (role_id, permission_id),
    KEY idx_role_permissions_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE users (
    id CHAR(36) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(20) NOT NULL,
    avatar_object_key VARCHAR(512) NULL,
    role_id CHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    onboarding_completed TINYINT(1) NOT NULL DEFAULT 0,
    must_change_password TINYINT(1) NOT NULL DEFAULT 0,
    auth_version INT NOT NULL DEFAULT 0,
    last_login_at DATETIME(3) NULL,
    cancelled_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_status_created_at (status, created_at),
    KEY idx_users_role_id (role_id),
    KEY idx_users_dashboard_role_status_created_at (role_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE refresh_tokens (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    parent_token_id CHAR(36) NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    revoke_reason VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_token_hash (token_hash),
    KEY idx_refresh_tokens_user_expires_at (user_id, expires_at),
    KEY idx_refresh_tokens_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE temporary_passwords (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_by_admin_id CHAR(36) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    used_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_temporary_passwords_user_expires_at (user_id, expires_at),
    KEY idx_temporary_passwords_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE admin_audit_logs (
    id CHAR(36) NOT NULL,
    admin_user_id CHAR(36) NOT NULL,
    target_user_id CHAR(36) NULL,
    action VARCHAR(64) NOT NULL,
    result VARCHAR(16) NOT NULL,
    request_id VARCHAR(64) NULL,
    detail_json JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_admin_audit_logs_admin_created_at (admin_user_id, created_at),
    KEY idx_admin_audit_logs_target_created_at (target_user_id, created_at),
    KEY idx_admin_audit_logs_created_at_id (created_at, id),
    KEY idx_admin_audit_logs_action_result_created_at (action, result, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE preference_presets (
    id CHAR(36) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    code VARCHAR(64) NOT NULL,
    label VARCHAR(20) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_preference_presets_code (code),
    KEY idx_preference_presets_kind_active_sort (kind, is_active, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE preference_items (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    preset_code VARCHAR(64) NULL,
    custom_value VARCHAR(20) NULL,
    normalized_value VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_preference_items_user_kind (user_id, kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_budget_histories (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    budget_enabled TINYINT(1) NOT NULL,
    daily_budget DECIMAL(12,2) NULL,
    effective_date DATE NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_budget_histories_user_effective_date (user_id, effective_date),
    KEY idx_user_budget_histories_user_effective_date (user_id, effective_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE food_default_templates (
    id CHAR(36) NOT NULL,
    template_version VARCHAR(32) NOT NULL,
    name VARCHAR(10) NOT NULL,
    normalized_name VARCHAR(64) NOT NULL,
    category VARCHAR(10) NOT NULL,
    default_price DECIMAL(12,2) NOT NULL,
    tags_json JSON NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_food_default_templates_version_name_category (template_version, normalized_name, category),
    KEY idx_food_default_templates_active_sort (is_active, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE food_options (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    name VARCHAR(10) NOT NULL,
    normalized_name VARCHAR(64) NOT NULL,
    category VARCHAR(10) NOT NULL,
    default_price DECIMAL(12,2) NOT NULL,
    source VARCHAR(16) NOT NULL,
    active_unique_key VARCHAR(160) NULL,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_food_options_user_active_key (user_id, active_unique_key),
    KEY idx_food_options_user_deleted_created_at (user_id, deleted_at, created_at),
    KEY idx_food_options_user_category_deleted_at (user_id, category, deleted_at),
    KEY idx_food_options_user_name (user_id, normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE food_option_tags (
    id CHAR(36) NOT NULL,
    food_option_id CHAR(36) NOT NULL,
    tag VARCHAR(20) NOT NULL,
    normalized_tag VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_food_option_tags_food_tag (food_option_id, normalized_tag),
    KEY idx_food_option_tags_normalized_tag_food (normalized_tag, food_option_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE diet_records (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    food_option_id CHAR(36) NULL,
    food_name_snapshot VARCHAR(10) NOT NULL,
    category_snapshot VARCHAR(10) NULL,
    tags_snapshot_json JSON NOT NULL,
    actual_price DECIMAL(12,2) NOT NULL,
    meal_type VARCHAR(16) NOT NULL,
    eaten_at DATETIME(3) NOT NULL,
    business_date DATE NOT NULL,
    source VARCHAR(20) NOT NULL,
    source_reference_id CHAR(36) NULL,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_diet_records_user_business_date (user_id, business_date),
    KEY idx_diet_records_user_eaten_at (user_id, eaten_at),
    KEY idx_diet_records_user_deleted_at (user_id, deleted_at),
    KEY idx_diet_records_user_source_date (user_id, source, business_date),
    KEY idx_diet_records_dashboard_deleted_business_source_user (deleted_at, business_date, source, user_id),
    KEY idx_diet_records_dashboard_deleted_created_user (deleted_at, created_at, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE slot_spins (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    selected_food_option_id CHAR(36) NOT NULL,
    selected_name_snapshot VARCHAR(10) NOT NULL,
    selected_category_snapshot VARCHAR(10) NULL,
    selected_price_snapshot DECIMAL(12,2) NOT NULL,
    selected_tags_snapshot_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'GENERATED',
    confirmed_diet_record_id CHAR(36) NULL,
    expires_at DATETIME(3) NOT NULL,
    confirmed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_slot_spins_user_status_expires_at (user_id, status, expires_at),
    KEY idx_slot_spins_expires_at (expires_at),
    KEY idx_slot_spins_dashboard_created_status_user (created_at, status, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO roles (id, code, name) VALUES
    ('00000000-0000-0000-0000-000000000101', 'USER', '普通用户'),
    ('00000000-0000-0000-0000-000000000102', 'ADMIN', '管理员');

INSERT INTO permissions (id, code, name, description) VALUES
    ('00000000-0000-0000-0000-000000000201', 'ACCOUNT_SELF_VIEW', '读取本人资料', '读取当前认证账号的公开资料'),
    ('00000000-0000-0000-0000-000000000202', 'ACCOUNT_CHANGE_PASSWORD', '修改本人密码', '修改正式或临时密码'),
    ('00000000-0000-0000-0000-000000000203', 'ACCOUNT_CANCEL', '注销本人账号', '将普通用户账号软注销'),
    ('00000000-0000-0000-0000-000000000204', 'FOOD_LIST', '食物列表', '分页筛选本人食物池'),
    ('00000000-0000-0000-0000-000000000205', 'FOOD_VIEW', '食物详情', '读取本人食物详情'),
    ('00000000-0000-0000-0000-000000000206', 'FOOD_CREATE', '创建食物', '向本人食物池新增食物'),
    ('00000000-0000-0000-0000-000000000207', 'FOOD_UPDATE', '修改食物', '修改本人食物池'),
    ('00000000-0000-0000-0000-000000000208', 'FOOD_DELETE', '删除食物', '软删除本人食物'),
    ('00000000-0000-0000-0000-000000000209', 'DIET_LIST', '饮食列表', '查询本人饮食记录'),
    ('00000000-0000-0000-0000-000000000210', 'DIET_CREATE', '创建饮食记录', '创建本人饮食记录'),
    ('00000000-0000-0000-0000-000000000211', 'DIET_UPDATE', '修改饮食记录', '修改本人饮食记录'),
    ('00000000-0000-0000-0000-000000000212', 'DIET_DELETE', '删除饮食记录', '软删除本人饮食记录'),
    ('00000000-0000-0000-0000-000000000213', 'DIET_STATISTICS', '饮食统计', '统计本人消费记录'),
    ('00000000-0000-0000-0000-000000000214', 'SLOT_SPIN', '老虎机抽取', '生成本人随机食物结果'),
    ('00000000-0000-0000-0000-000000000215', 'SLOT_CONFIRM', '老虎机确认', '确认老虎机结果并记录饮食');

INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000101', id FROM permissions;

INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000102', id
FROM permissions WHERE code IN ('ACCOUNT_SELF_VIEW', 'ACCOUNT_CHANGE_PASSWORD');

INSERT INTO preference_presets (id, kind, code, label, sort_order) VALUES
    ('00000000-0000-4000-8000-000000000001', 'TASTE', 'TASTE_LIGHT', '清淡', 1),
    ('00000000-0000-4000-8000-000000000002', 'TASTE', 'TASTE_SPICY', '偏辣', 2),
    ('00000000-0000-4000-8000-000000000003', 'TASTE', 'TASTE_SWEET', '偏甜', 3),
    ('00000000-0000-4000-8000-000000000004', 'TASTE', 'TASTE_SALTY', '偏咸', 4),
    ('00000000-0000-4000-8000-000000000005', 'MEDICAL_ALLERGY', 'ALLERGY_PEANUT', '花生过敏', 1),
    ('00000000-0000-4000-8000-000000000006', 'MEDICAL_ALLERGY', 'ALLERGY_SEAFOOD', '海鲜过敏', 2),
    ('00000000-0000-4000-8000-000000000007', 'MEDICAL_ALLERGY', 'ALLERGY_DAIRY', '乳制品过敏', 3),
    ('00000000-0000-4000-8000-000000000008', 'MEDICAL_ALLERGY', 'ALLERGY_EGG', '蛋类过敏', 4),
    ('00000000-0000-4000-8000-000000000009', 'DIETARY_RESTRICTION', 'RESTRICTION_VEGETARIAN', '素食', 1),
    ('00000000-0000-4000-8000-000000000010', 'DIETARY_RESTRICTION', 'RESTRICTION_NO_PORK', '不吃猪肉', 2),
    ('00000000-0000-4000-8000-000000000011', 'DIETARY_RESTRICTION', 'RESTRICTION_NO_BEEF', '不吃牛肉', 3),
    ('00000000-0000-4000-8000-000000000012', 'DIETARY_RESTRICTION', 'RESTRICTION_NO_OFFAL', '不吃动物内脏', 4),
    ('00000000-0000-4000-8000-000000000013', 'DISLIKE', 'DISLIKE_CILANTRO', '不吃香菜', 1),
    ('00000000-0000-4000-8000-000000000014', 'DISLIKE', 'DISLIKE_SCALLION', '不吃葱', 2),
    ('00000000-0000-4000-8000-000000000015', 'DISLIKE', 'DISLIKE_GINGER', '不吃姜', 3),
    ('00000000-0000-4000-8000-000000000016', 'DISLIKE', 'DISLIKE_GARLIC', '不吃蒜', 4);

INSERT INTO food_default_templates (id, template_version, name, normalized_name, category, default_price, tags_json, sort_order) VALUES
    ('10000000-0000-4000-8000-000000000001', 'v1', '鸡腿饭', '鸡腿饭', '米饭', 18.00, JSON_ARRAY('主食', '肉类', '咸香'), 1),
    ('10000000-0000-4000-8000-000000000002', 'v1', '番茄鸡蛋饭', '番茄鸡蛋饭', '米饭', 15.00, JSON_ARRAY('主食', '蛋类', '清淡'), 2),
    ('10000000-0000-4000-8000-000000000003', 'v1', '牛肉面', '牛肉面', '面食', 20.00, JSON_ARRAY('主食', '肉类', '汤面'), 3),
    ('10000000-0000-4000-8000-000000000004', 'v1', '馄饨', '馄饨', '小吃', 15.00, JSON_ARRAY('汤食', '清淡', '馅料'), 4),
    ('10000000-0000-4000-8000-000000000005', 'v1', '麻辣烫', '麻辣烫', '小吃', 25.00, JSON_ARRAY('可自选', '辛辣', '热食'), 5),
    ('10000000-0000-4000-8000-000000000006', 'v1', '炒饭', '炒饭', '米饭', 15.00, JSON_ARRAY('主食', '快捷', '热食'), 6),
    ('10000000-0000-4000-8000-000000000007', 'v1', '汉堡', '汉堡', '快餐', 22.00, JSON_ARRAY('快餐', '肉类', '方便'), 7),
    ('10000000-0000-4000-8000-000000000008', 'v1', '饺子', '饺子', '面食', 18.00, JSON_ARRAY('主食', '馅料', '热食'), 8),
    ('10000000-0000-4000-8000-000000000009', 'v1', '轻食沙拉', '轻食沙拉', '轻食', 20.00, JSON_ARRAY('蔬菜', '清淡', '轻食'), 9),
    ('10000000-0000-4000-8000-000000000010', 'v1', '小米粥', '小米粥', '汤粥', 10.00, JSON_ARRAY('清淡', '汤食', '早餐'), 10);
