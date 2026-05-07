-- Drop the redundant expires_at column that was auto-created by Hibernate DDL.
-- The canonical expiry column is expiry_date (from the original schema).
-- The entity now correctly maps expiresAt -> expiry_date via @Column(name = "expiry_date").
ALTER TABLE email_verification_tokens DROP COLUMN IF EXISTS expires_at;
