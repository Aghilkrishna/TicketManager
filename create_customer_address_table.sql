-- Migration script for Customer Address Management Feature
-- This script creates the customer_address table and updates the tickets table

-- Create customer_address table
CREATE TABLE IF NOT EXISTS customer_address (
    id BIGSERIAL PRIMARY KEY,
    customer_name VARCHAR(150),
    customer_email VARCHAR(100),
    customer_phone VARCHAR(20),
    flat VARCHAR(120),
    street VARCHAR(150),
    city VARCHAR(80),
    state VARCHAR(80),
    pincode VARCHAR(20),
    location_link VARCHAR(1000),
    created_by_id BIGINT NOT NULL,
    updated_by_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key constraints
    FOREIGN KEY (created_by_id) REFERENCES app_user(id),
    FOREIGN KEY (updated_by_id) REFERENCES app_user(id)
);

-- Create trigger for automatic updated_at timestamp (PostgreSQL syntax)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_customer_address_updated_at 
    BEFORE UPDATE ON customer_address 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Add customer_address_id column to tickets table
ALTER TABLE tickets 
ADD COLUMN IF NOT EXISTS customer_address_id BIGINT;

-- Add index for better performance on customer address lookups
CREATE INDEX IF NOT EXISTS idx_customer_address_email_phone ON customer_address(customer_email, customer_phone);
CREATE INDEX IF NOT EXISTS idx_customer_address_created_by ON customer_address(created_by_id);

-- Add index for tickets customer_address_id
CREATE INDEX IF NOT EXISTS idx_tickets_customer_address_id ON tickets(customer_address_id);

-- Migrate existing customer addresses from tickets to customer_address table
-- This is a one-time migration to populate the new table with existing addresses
INSERT INTO customer_address (customer_name, customer_email, customer_phone, flat, street, city, state, pincode, location_link, created_by_id, updated_by_id, created_at, updated_at)
SELECT DISTINCT
    t.customer_name,
    t.customer_email,
    t.customer_phone,
    t.customer_flat,
    t.customer_street,
    t.customer_city,
    t.customer_state,
    t.customer_pincode,
    t.customer_location_link,
    t.created_by_id,
    t.updated_by_id,
    t.created_at,
    t.updated_at
FROM tickets t
WHERE t.customer_email IS NOT NULL 
  AND t.customer_phone IS NOT NULL
  AND (
    t.customer_flat IS NOT NULL 
    OR t.customer_street IS NOT NULL 
    OR t.customer_city IS NOT NULL 
    OR t.customer_state IS NOT NULL 
    OR t.customer_pincode IS NOT NULL
  )
  AND NOT EXISTS (
    SELECT 1 FROM customer_address ca 
    WHERE ca.customer_email = t.customer_email 
      AND ca.customer_phone = t.customer_phone
      AND (
        (ca.flat = t.customer_flat OR (ca.flat IS NULL AND t.customer_flat IS NULL))
        AND (ca.street = t.customer_street OR (ca.street IS NULL AND t.customer_street IS NULL))
        AND (ca.city = t.customer_city OR (ca.city IS NULL AND t.customer_city IS NULL))
        AND (ca.state = t.customer_state OR (ca.state IS NULL AND t.customer_state IS NULL))
        AND (ca.pincode = t.customer_pincode OR (ca.pincode IS NULL AND t.customer_pincode IS NULL))
      )
  );

-- Update tickets to reference the new customer_address records
-- This will map existing tickets to the newly created customer addresses
UPDATE tickets t
SET customer_address_id = (
    SELECT ca.id 
    FROM customer_address ca 
    WHERE ca.customer_email = t.customer_email 
      AND ca.customer_phone = t.customer_phone
      AND (
        (ca.flat = t.customer_flat OR (ca.flat IS NULL AND t.customer_flat IS NULL))
        AND (ca.street = t.customer_street OR (ca.street IS NULL AND t.customer_street IS NULL))
        AND (ca.city = t.customer_city OR (ca.city IS NULL AND t.customer_city IS NULL))
        AND (ca.state = t.customer_state OR (ca.state IS NULL AND t.customer_state IS NULL))
        AND (ca.pincode = t.customer_pincode OR (ca.pincode IS NULL AND t.customer_pincode IS NULL))
      )
    LIMIT 1
)
WHERE t.customer_email IS NOT NULL 
  AND t.customer_phone IS NOT NULL
  AND t.customer_address_id IS NULL;
