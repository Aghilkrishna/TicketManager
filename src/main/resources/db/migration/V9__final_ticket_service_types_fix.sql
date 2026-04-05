-- Final comprehensive fix for ticket service types
-- This migration ensures all old values are converted properly

-- Drop and recreate with proper constraints to ensure data integrity
DO $$ 
BEGIN
    -- Check if the column exists and has old values
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'tickets' AND column_name = 'service_type') THEN
        -- Update all possible variations of old values
        UPDATE tickets SET service_type = 'AMC' 
        WHERE service_type IN ('MAINTENANCE', 'maintenance', 'Maintenance');
        
        UPDATE tickets SET service_type = 'SERVICE' 
        WHERE service_type IN ('REPAIR', 'repair', 'Repair');
        
        UPDATE tickets SET service_type = 'INSTALLATION' 
        WHERE service_type IN ('INSTALLATION', 'installation', 'Installation');
        
        -- Handle any NULL or empty values
        UPDATE tickets SET service_type = 'INSTALLATION' 
        WHERE service_type IS NULL OR service_type = '' OR service_type = ' ';
        
        -- Handle any unexpected values
        UPDATE tickets SET service_type = 'INSTALLATION' 
        WHERE service_type NOT IN ('INSTALLATION', 'SERVICE', 'AMC', 'SITE_VISIT');
        
        -- Add a check constraint to prevent invalid values in the future
        ALTER TABLE tickets 
        ADD CONSTRAINT IF NOT EXISTS chk_service_type 
        CHECK (service_type IN ('INSTALLATION', 'SERVICE', 'AMC', 'SITE_VISIT'));
    END IF;
END $$;

-- Verify the results
SELECT service_type, COUNT(*) as count 
FROM tickets 
GROUP BY service_type 
ORDER BY service_type;
