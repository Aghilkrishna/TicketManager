--- Add CANCELLED status to ticket status enum
--- This migration ensures the CANCELLED status is properly supported

-- Drop any existing check constraint on ticket status if it exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.check_constraints 
        WHERE constraint_name = 'tickets_status_check'
    ) THEN
        ALTER TABLE tickets DROP CONSTRAINT tickets_status_check;
    END IF;
END $$;

-- Add a new check constraint that includes CANCELLED status
ALTER TABLE tickets 
ADD CONSTRAINT tickets_status_check 
CHECK (status IN ('OPEN', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED', 'CLOSED', 'CANCELLED'));

-- Verify the constraint was added successfully
SELECT constraint_name, check_clause 
FROM information_schema.check_constraints 
WHERE constraint_name = 'tickets_status_check';
