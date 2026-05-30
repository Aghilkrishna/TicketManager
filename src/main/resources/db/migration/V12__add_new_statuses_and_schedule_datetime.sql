-- Change schedule_date from DATE to TIMESTAMP to store date and time
ALTER TABLE tickets ALTER COLUMN schedule_date TYPE TIMESTAMP USING schedule_date::TIMESTAMP;
