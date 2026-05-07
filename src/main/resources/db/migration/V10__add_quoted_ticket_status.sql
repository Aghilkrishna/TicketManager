-- Add QUOTED status to ticket_status enum
-- This migration adds the new QUOTED status to the existing ticket_status enum type

-- First, add the new value to the enum type
ALTER TYPE ticket_status ADD VALUE 'QUOTED';

-- Note: The QUOTED status is now available for use in the tickets table
-- No data migration is needed as this is just adding a new enum value
