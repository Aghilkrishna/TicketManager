ALTER TABLE roles ADD COLUMN IF NOT EXISTS description VARCHAR(255);
ALTER TABLE roles ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_name_check;

UPDATE roles
SET active = TRUE
WHERE active IS NULL;

CREATE TABLE IF NOT EXISTS role_features (
    role_id BIGINT NOT NULL,
    feature_name VARCHAR(60) NOT NULL,
    CONSTRAINT fk_role_features_role
        FOREIGN KEY (role_id) REFERENCES roles(id)
);
ALTER TABLE role_features DROP CONSTRAINT IF EXISTS role_features_feature_name_check;

CREATE TABLE IF NOT EXISTS email_notification_settings (
    action_name VARCHAR(60) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS first_name VARCHAR(80);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_name VARCHAR(80);
ALTER TABLE users ADD COLUMN IF NOT EXISTS company_name VARCHAR(150);
ALTER TABLE users ADD COLUMN IF NOT EXISTS contact_person VARCHAR(120);
ALTER TABLE users ADD COLUMN IF NOT EXISTS gst_number VARCHAR(30);
ALTER TABLE users ADD COLUMN IF NOT EXISTS flat VARCHAR(120);
ALTER TABLE users ADD COLUMN IF NOT EXISTS building VARCHAR(120);
ALTER TABLE users ADD COLUMN IF NOT EXISTS area VARCHAR(120);
ALTER TABLE users ADD COLUMN IF NOT EXISTS city VARCHAR(80);
ALTER TABLE users ADD COLUMN IF NOT EXISTS state VARCHAR(80);
ALTER TABLE users ADD COLUMN IF NOT EXISTS country VARCHAR(80);
ALTER TABLE users ADD COLUMN IF NOT EXISTS pincode VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_image BYTEA;
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_image_content_type VARCHAR(100);

ALTER TABLE tickets ADD COLUMN IF NOT EXISTS address VARCHAR(500);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS service_type VARCHAR(30);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS location_link VARCHAR(1000);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS site_visits INTEGER NOT NULL DEFAULT 0;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS parent_ticket_id BIGINT;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS vendor_user_id BIGINT;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS vendor_notes VARCHAR(500);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS customer_name VARCHAR(150);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS customer_flat VARCHAR(120);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS customer_street VARCHAR(150);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS customer_city VARCHAR(80);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS customer_state VARCHAR(80);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS customer_pincode VARCHAR(20);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS customer_location_link VARCHAR(1000);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS pricing_model VARCHAR(30);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS estimated_cost NUMERIC(12,2);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS actual_cost NUMERIC(12,2);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS billing_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID';
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS billing_paid_at TIMESTAMP;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS additional_notes VARCHAR(3000);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS updated_by_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_tickets_created_by_id ON tickets(created_by_id);
CREATE INDEX IF NOT EXISTS idx_tickets_assigned_to_id ON tickets(assigned_to_id);
CREATE INDEX IF NOT EXISTS idx_tickets_vendor_user_id ON tickets(vendor_user_id);
CREATE INDEX IF NOT EXISTS idx_tickets_status_priority ON tickets(status, priority);
CREATE INDEX IF NOT EXISTS idx_tickets_updated_at ON tickets(updated_at);

CREATE TABLE IF NOT EXISTS ticket_site_visits (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    visited_at TIMESTAMP NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    notes VARCHAR(2000),
    CONSTRAINT fk_ticket_site_visits_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_site_visits_agent FOREIGN KEY (agent_id) REFERENCES users(id)
);
ALTER TABLE ticket_site_visits ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE ticket_site_visits ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
CREATE INDEX IF NOT EXISTS idx_ticket_service_users_user_ticket ON ticket_service_users(user_id, ticket_id);
CREATE INDEX IF NOT EXISTS idx_ticket_comments_ticket_created_at ON ticket_comments(ticket_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ticket_site_visits_ticket_visited_at ON ticket_site_visits(ticket_id, visited_at);
CREATE INDEX IF NOT EXISTS idx_notifications_user_read_created_at ON notifications(user_id, read_flag, created_at);
CREATE INDEX IF NOT EXISTS idx_chat_messages_conversation_created_at ON chat_messages(conversation_id, created_at);
CREATE INDEX IF NOT EXISTS idx_chat_messages_unread_lookup ON chat_messages(conversation_id, sender_id, read_at);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_participants_user_conversation ON chat_conversation_participants(user_id, conversation_id);
