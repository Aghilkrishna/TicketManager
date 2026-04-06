-- Create user_id_proofs table for multiple ID proofs per user
CREATE TABLE user_id_proofs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    id_proof_type VARCHAR(50) NOT NULL,
    id_proof_document BYTEA,
    id_proof_content_type VARCHAR(100),
    id_proof_file_name VARCHAR(200),
    file_size BIGINT,
    upload_status VARCHAR(20) DEFAULT 'UPLOADED',
    verified BOOLEAN DEFAULT FALSE,
    verification_notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_user_id_proof_type UNIQUE (user_id, id_proof_type)
);

-- Create indexes for better performance
CREATE INDEX idx_user_id_proofs_user_id ON user_id_proofs(user_id);
CREATE INDEX idx_user_id_proofs_type ON user_id_proofs(id_proof_type);
CREATE INDEX idx_user_id_proofs_status ON user_id_proofs(upload_status);
CREATE INDEX idx_user_id_proofs_verified ON user_id_proofs(verified);
