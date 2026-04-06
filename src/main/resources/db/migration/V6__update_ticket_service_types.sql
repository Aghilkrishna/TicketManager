-- Update ticket service types
-- Update existing MAINTENANCE to AMC
UPDATE tickets SET service_type = 'AMC' WHERE service_type = 'MAINTENANCE';

-- Update existing REPAIR to SERVICE  
UPDATE tickets SET service_type = 'SERVICE' WHERE service_type = 'REPAIR';

-- Note: SITE_VISIT is a new type, no existing records need updating
-- New tickets will be able to use SITE_VISIT when created through the updated UI
