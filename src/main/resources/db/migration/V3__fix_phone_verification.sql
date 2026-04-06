-- Fix phone_verified column for existing users
-- First, make the column nullable temporarily
ALTER TABLE users ALTER COLUMN phone_verified DROP NOT NULL;

-- Update all NULL values to false
UPDATE users SET phone_verified = false WHERE phone_verified IS NULL;

-- Now add the NOT NULL constraint
ALTER TABLE users ALTER COLUMN phone_verified SET NOT NULL;
