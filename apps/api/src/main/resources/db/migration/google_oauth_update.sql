-- Migration to support Google OAuth and nullable passwords
-- Run this against your PostgreSQL database

-- 1. Add new columns for Google OAuth
ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_provider VARCHAR(255) DEFAULT 'LOCAL';
ALTER TABLE users ADD COLUMN IF NOT EXISTS google_id VARCHAR(255) UNIQUE;

-- 2. Drop NOT NULL constraint on password (social logins won't have a password)
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;
