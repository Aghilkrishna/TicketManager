-- Fix ticket service types - comprehensive approach
-- This migration handles the enum conversion properly

-- First, let's see what values exist and update them
-- Update existing MAINTENANCE to AMC
UPDATE tickets SET service_type = 'AMC' WHERE service_type = 'MAINTENANCE';

-- Update existing REPAIR to SERVICE  
UPDATE tickets SET service_type = 'SERVICE' WHERE service_type = 'REPAIR';

-- Handle any NULL values by setting a default
UPDATE tickets SET service_type = 'INSTALLATION' WHERE service_type IS NULL;

-- Verify the updates (optional, for debugging)
-- SELECT service_type, COUNT(*) FROM tickets GROUP BY service_type;

-- Note: SITE_VISIT is a new type, no existing records need updating
-- New tickets will be able to use SITE_VISIT when created through the updated UI
