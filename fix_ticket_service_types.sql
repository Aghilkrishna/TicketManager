-- Manual fix for ticket service types
-- Run this script manually if migrations haven't been applied yet

-- Update existing MAINTENANCE to AMC
UPDATE tickets SET service_type = 'AMC' WHERE service_type = 'MAINTENANCE';

-- Update existing REPAIR to SERVICE  
UPDATE tickets SET service_type = 'SERVICE' WHERE service_type = 'REPAIR';

-- Handle any NULL values by setting a default
UPDATE tickets SET service_type = 'INSTALLATION' WHERE service_type IS NULL;

-- Show the results
SELECT service_type, COUNT(*) as count 
FROM tickets 
GROUP BY service_type 
ORDER BY service_type;

-- This should show: INSTALLATION, SERVICE, AMC, SITE_VISIT (if any)
