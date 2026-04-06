-- IMMEDIATE FIX for TicketServiceType.MAINTENANCE error
-- Run this SQL directly in your database to fix existing data

-- Step 1: Update all old MAINTENANCE values to AMC
UPDATE tickets SET service_type = 'AMC' WHERE service_type = 'MAINTENANCE';

-- Step 2: Update all old REPAIR values to SERVICE  
UPDATE tickets SET service_type = 'SERVICE' WHERE service_type = 'REPAIR';

-- Step 3: Handle any case variations
UPDATE tickets SET service_type = 'AMC' WHERE UPPER(service_type) = 'MAINTENANCE';
UPDATE tickets SET service_type = 'SERVICE' WHERE UPPER(service_type) = 'REPAIR';

-- Step 4: Set default for any NULL/empty values
UPDATE tickets SET service_type = 'INSTALLATION' WHERE service_type IS NULL OR service_type = '';

-- Step 5: Verify the fix
SELECT service_type, COUNT(*) as count 
FROM tickets 
GROUP BY service_type 
ORDER BY service_type;

-- Expected results should show only: INSTALLATION, SERVICE, AMC, SITE_VISIT
