-- V7: Create password_reset_tokens table
-- Stores short-lived tokens (60-min TTL) for the forgot-password flow.
-- Token is a UUID string; unique index ensures no collision.

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(255) NOT NULL,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expiry_date TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_prt_token UNIQUE (token)
);

CREATE INDEX IF NOT EXISTS idx_prt_user_id ON password_reset_tokens (user_id);
