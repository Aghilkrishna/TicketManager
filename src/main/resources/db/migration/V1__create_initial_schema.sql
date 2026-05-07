-- Create initial database schema for TicketManager

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(120) NOT NULL UNIQUE,
    phone VARCHAR(30) UNIQUE,
    first_name VARCHAR(80),
    last_name VARCHAR(80),
    company_name VARCHAR(150),
    contact_person VARCHAR(120),
    gst_number VARCHAR(30),
    flat VARCHAR(120),
    building VARCHAR(120),
    area VARCHAR(120),
    city VARCHAR(80),
    state VARCHAR(80),
    country VARCHAR(80),
    pincode VARCHAR(20),
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    phone_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    profile_image BYTEA,
    profile_image_content_type VARCHAR(100)
);

-- Create roles table
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Create user_roles junction table
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- Create role_features table
CREATE TABLE IF NOT EXISTS role_features (
    role_id BIGINT NOT NULL,
    feature_name VARCHAR(60) NOT NULL,
    PRIMARY KEY (role_id, feature_name),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- Create tickets table
CREATE TABLE IF NOT EXISTS tickets (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    created_by_id BIGINT NOT NULL,
    assigned_to_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    address VARCHAR(500),
    service_type VARCHAR(30),
    location_link VARCHAR(1000),
    site_visits INTEGER NOT NULL DEFAULT 0,
    parent_ticket_id BIGINT,
    vendor_user_id BIGINT,
    vendor_notes VARCHAR(500),
    customer_name VARCHAR(150),
    customer_flat VARCHAR(120),
    customer_street VARCHAR(150),
    customer_city VARCHAR(80),
    customer_state VARCHAR(80),
    customer_pincode VARCHAR(20),
    customer_location_link VARCHAR(1000),
    customer_address_id BIGINT,
    pricing_model VARCHAR(30),
    estimated_cost NUMERIC(12,2),
    actual_cost NUMERIC(12,2),
    billing_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    billing_paid_at TIMESTAMP,
    additional_notes VARCHAR(3000),
    updated_by_id BIGINT,
    schedule_date DATE,
    FOREIGN KEY (created_by_id) REFERENCES users(id),
    FOREIGN KEY (assigned_to_id) REFERENCES users(id),
    FOREIGN KEY (vendor_user_id) REFERENCES users(id),
    FOREIGN KEY (parent_ticket_id) REFERENCES tickets(id),
    FOREIGN KEY (updated_by_id) REFERENCES users(id)
);

-- Create customer_addresses table
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
    FOREIGN KEY (created_by_id) REFERENCES users(id),
    FOREIGN KEY (updated_by_id) REFERENCES users(id)
);

-- Create ticket_comments table
CREATE TABLE IF NOT EXISTS ticket_comments (
    id BIGSERIAL PRIMARY KEY,
    content VARCHAR(2000) NOT NULL,
    ticket_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    parent_comment_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    FOREIGN KEY (author_id) REFERENCES users(id),
    FOREIGN KEY (parent_comment_id) REFERENCES ticket_comments(id) ON DELETE CASCADE
);

-- Create ticket_site_visits table
CREATE TABLE IF NOT EXISTS ticket_site_visits (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    visited_at TIMESTAMP NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    notes VARCHAR(2000),
    FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    FOREIGN KEY (agent_id) REFERENCES users(id)
);

-- Create notifications table
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    message VARCHAR(500) NOT NULL,
    reference_type VARCHAR(50),
    reference_id BIGINT,
    read_flag BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create email_notification_settings table
CREATE TABLE IF NOT EXISTS email_notification_settings (
    action_name VARCHAR(60) PRIMARY KEY,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled BOOLEAN NOT NULL DEFAULT FALSE
);

-- Create chat_conversations table
CREATE TABLE IF NOT EXISTS chat_conversations (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create chat_messages table
CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(id)
);

-- Create chat_conversation_participants table
CREATE TABLE IF NOT EXISTS chat_conversation_participants (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE(conversation_id, user_id)
);

-- Create user_id_proofs table
CREATE TABLE IF NOT EXISTS user_id_proofs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    id_proof_type VARCHAR(50) NOT NULL,
    id_proof_document BYTEA,
    id_proof_content_type VARCHAR(100),
    id_proof_file_name VARCHAR(200),
    file_size BIGINT,
    upload_status VARCHAR(20) DEFAULT 'UPLOADED',
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verification_notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create email_verification_tokens table
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create mobile_verification_tokens table
CREATE TABLE IF NOT EXISTS mobile_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(10) NOT NULL,
    user_id BIGINT NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create password_reset_tokens table
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create ticket_attachments table
CREATE TABLE IF NOT EXISTS ticket_attachments (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_content BYTEA NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100),
    uploaded_by_id BIGINT NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    FOREIGN KEY (uploaded_by_id) REFERENCES users(id)
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);

CREATE INDEX IF NOT EXISTS idx_tickets_created_by_id ON tickets(created_by_id);
CREATE INDEX IF NOT EXISTS idx_tickets_assigned_to_id ON tickets(assigned_to_id);
CREATE INDEX IF NOT EXISTS idx_tickets_vendor_user_id ON tickets(vendor_user_id);
CREATE INDEX IF NOT EXISTS idx_tickets_status_priority ON tickets(status, priority);
CREATE INDEX IF NOT EXISTS idx_tickets_updated_at ON tickets(updated_at);

CREATE INDEX IF NOT EXISTS idx_ticket_comments_ticket_created_at ON ticket_comments(ticket_id, created_at);

CREATE INDEX IF NOT EXISTS idx_ticket_site_visits_ticket_visited_at ON ticket_site_visits(ticket_id, visited_at);

CREATE INDEX IF NOT EXISTS idx_notifications_user_read_created_at ON notifications(user_id, read_flag, created_at);

CREATE INDEX IF NOT EXISTS idx_chat_messages_conversation_created_at ON chat_messages(conversation_id, created_at);
CREATE INDEX IF NOT EXISTS idx_chat_messages_unread_lookup ON chat_messages(conversation_id, sender_id, read_at);

CREATE INDEX IF NOT EXISTS idx_chat_conversation_participants_user_conversation ON chat_conversation_participants(user_id, conversation_id);

CREATE INDEX IF NOT EXISTS idx_customer_address_created_by_id ON customer_address(created_by_id);
