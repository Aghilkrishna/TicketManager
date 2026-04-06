-- Add ID proof fields to users table
ALTER TABLE users ADD COLUMN id_proof_type VARCHAR(50);
ALTER TABLE users ADD COLUMN id_proof_document BYTEA;
ALTER TABLE users ADD COLUMN id_proof_content_type VARCHAR(100);
ALTER TABLE users ADD COLUMN id_proof_file_name VARCHAR(200);
