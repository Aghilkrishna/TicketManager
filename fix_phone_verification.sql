-- Manual fix for phone_verified column issue
-- Run this script directly in PostgreSQL if Flyway migrations are not working

-- Fix existing NULL values
UPDATE users SET phone_verified = false WHERE phone_verified IS NULL;

-- Make the column NOT NULL (this might fail if there are still NULL values)
-- If it fails, run the UPDATE statement again first
ALTER TABLE users ALTER COLUMN phone_verified SET NOT NULL;

-- Verify the fix
SELECT 
    COUNT(*) as total_users,
    COUNT(CASE WHEN phone_verified = true THEN 1 END) as verified_users,
    COUNT(CASE WHEN phone_verified = false THEN 1 END) as unverified_users,
    COUNT(CASE WHEN phone_verified IS NULL THEN 1 END) as null_users
FROM users;
