ALTER TABLE resumes ADD COLUMN file_hash VARCHAR(64);
CREATE INDEX idx_resumes_file_hash ON resumes(file_hash);
