-- 删除当前未投入使用的医疗过敏功能及其已有敏感数据；保留登录、隐私和年龄同意记录。
DELETE FROM preference_items WHERE kind = 'MEDICAL_ALLERGY';
DELETE FROM preference_presets WHERE kind = 'MEDICAL_ALLERGY';
DELETE FROM consent_events WHERE kind = 'MEDICAL';

ALTER TABLE user_consents
    DROP COLUMN medical_version,
    DROP COLUMN medical_accepted_at;
