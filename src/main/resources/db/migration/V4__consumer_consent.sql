-- Additive schema: never change V1-V3 checksums. Existing consent is not inferred.
CREATE TABLE user_consents (
    user_id CHAR(36) NOT NULL PRIMARY KEY,
    terms_version VARCHAR(32) NOT NULL,
    privacy_version VARCHAR(32) NOT NULL,
    age_band VARCHAR(16) NOT NULL,
    accepted_at DATETIME(3) NOT NULL,
    medical_version VARCHAR(32) NULL,
    medical_accepted_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE consent_events (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    version VARCHAR(32) NOT NULL,
    accepted BOOLEAN NOT NULL,
    created_at DATETIME(3) NOT NULL,
    INDEX idx_consent_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Force consumer sessions to reauthenticate; administrator credentials remain intact.
UPDATE users u JOIN roles r ON r.id=u.role_id SET u.auth_version=u.auth_version+1,u.must_change_password=0 WHERE r.code='USER';
UPDATE refresh_tokens t JOIN users u ON u.id=t.user_id JOIN roles r ON r.id=u.role_id
SET t.revoked_at=UTC_TIMESTAMP(3),t.revoke_reason='CONSENT_UPGRADE' WHERE r.code='USER' AND t.revoked_at IS NULL;
