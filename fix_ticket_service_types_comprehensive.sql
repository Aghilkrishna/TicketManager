-- Comprehensive fix for ticket service types
-- This script handles all possible scenarios and ensures data consistency

-- First, let's see what values currently exist (for debugging)
-- SELECT service_type, COUNT(*) FROM tickets GROUP BY service_type;

-- Update existing MAINTENANCE to AMC
UPDATE tickets SET service_type = 'AMC' WHERE service_type = 'MAINTENANCE';

-- Update existing REPAIR to SERVICE  
UPDATE tickets SET service_type = 'SERVICE' WHERE service_type = 'REPAIR';

-- Handle any lowercase or mixed case values
UPDATE tickets SET service_type = 'AMC' WHERE UPPER(service_type) = 'MAINTENANCE';
UPDATE tickets SET service_type = 'SERVICE' WHERE UPPER(service_type) = 'REPAIR';
UPDATE tickets SET service_type = 'INSTALLATION' WHERE UPPER(service_type) = 'INSTALLATION';

-- Handle any NULL values by setting a default
UPDATE tickets SET service_type = 'INSTALLATION' WHERE service_type IS NULL OR service_type = '';

-- Handle any unexpected values by setting them to INSTALLATION
UPDATE tickets SET service_type = 'INSTALLATION' 
WHERE service_type NOT IN ('INSTALLATION', 'SERVICE', 'AMC', 'SITE_VISIT');

-- Verify the updates
SELECT service_type, COUNT(*) as count 
FROM tickets 
GROUP BY service_type 
ORDER BY service_type;

-- This should show only: INSTALLATION, SERVICE, AMC, SITE_VISIT
