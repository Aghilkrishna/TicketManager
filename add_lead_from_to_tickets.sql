-- Adds Lead From support for Leads-type tickets
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS lead_from VARCHAR(120);
