-- Comprehensive fix for phone_verified column
-- This migration handles all edge cases for existing data

-- Step 1: Drop any existing constraints if they exist
DO $$ 
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.check_constraints 
        WHERE constraint_name = 'users_phone_verified_check'
    ) THEN
        ALTER TABLE users DROP CONSTRAINT users_phone_verified_check;
    END IF;
END $$;

-- Step 2: Make column nullable if it's not already
ALTER TABLE users ALTER COLUMN phone_verified DROP NOT NULL;

-- Step 3: Update all existing records to have a default value
UPDATE users SET phone_verified = false WHERE phone_verified IS NULL;

-- Step 4: Set a default value for future inserts
ALTER TABLE users ALTER COLUMN phone_verified SET DEFAULT false;

-- Step 5: Now add the NOT NULL constraint
ALTER TABLE users ALTER COLUMN phone_verified SET NOT NULL;

-- Step 6: Verify the fix
SELECT COUNT(*) as null_count FROM users WHERE phone_verified IS NULL;
