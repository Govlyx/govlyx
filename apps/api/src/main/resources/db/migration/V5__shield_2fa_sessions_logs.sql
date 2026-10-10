-- V5: Shield, 2FA, Sessions, and Login Logs Migration
-- Description: Adds shield default flag, 2FA fields, and tracking tables for sessions/login logs.

-- Rollback Note (Down Migration):
/*
DROP TABLE IF EXISTS user_login_logs;
DROP TABLE IF EXISTS user_sessions;
ALTER TABLE users 
    DROP COLUMN IF EXISTS two_factor_last_verified_at,
    DROP COLUMN IF EXISTS two_factor_secret_hash,
    DROP COLUMN IF EXISTS two_factor_method,
    DROP COLUMN IF EXISTS two_factor_enabled,
    DROP COLUMN IF EXISTS shield_enabled;
*/

-- 1. Shield Default Column
ALTER TABLE users 
    ADD COLUMN IF NOT EXISTS shield_enabled BOOLEAN DEFAULT true;

-- 2. 2FA Fields on Users
ALTER TABLE users 
    ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS two_factor_method VARCHAR(50) DEFAULT 'email',
    ADD COLUMN IF NOT EXISTS two_factor_secret_hash VARCHAR(255),
    ADD COLUMN IF NOT EXISTS two_factor_last_verified_at TIMESTAMP;

-- 3. Sessions Table
CREATE TABLE IF NOT EXISTS user_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_hash VARCHAR(255) NOT NULL,
    device VARCHAR(255),
    browser VARCHAR(255),
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP,
    revoked BOOLEAN DEFAULT false,
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_hash ON user_sessions(session_hash);

-- 4. Login Logs Table
CREATE TABLE IF NOT EXISTS user_login_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent TEXT,
    device VARCHAR(255),
    browser VARCHAR(255),
    result VARCHAR(50) NOT NULL,
    CONSTRAINT fk_user_login_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_login_logs_user_time ON user_login_logs(user_id, login_time);
CREATE INDEX IF NOT EXISTS idx_user_login_logs_time ON user_login_logs(login_time); -- For 180-day purge support
