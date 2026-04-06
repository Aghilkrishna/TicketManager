-- Add phone verification column to users table with default value for existing records
ALTER TABLE users ADD COLUMN phone_verified BOOLEAN DEFAULT false;
UPDATE users SET phone_verified = false WHERE phone_verified IS NULL;
ALTER TABLE users ALTER COLUMN phone_verified SET NOT NULL;
