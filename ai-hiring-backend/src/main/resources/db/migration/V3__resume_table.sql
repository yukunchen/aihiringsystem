CREATE TYPE resume_source AS ENUM ('MANUAL', 'BOSS');
CREATE TYPE resume_status AS ENUM ('UPLOADED', 'TEXT_EXTRACTED', 'AI_PROCESSED');

CREATE TABLE resumes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    raw_text TEXT,
    source resume_source NOT NULL DEFAULT 'MANUAL',
    status resume_status NOT NULL DEFAULT 'UPLOADED',
    uploaded_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    candidate_name VARCHAR(100),
    candidate_phone VARCHAR(50),
    candidate_email VARCHAR(100),
    education JSONB,
    experience JSONB,
    projects JSONB,
    skills JSONB,
    search_vector TSVECTOR,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_resumes_search_vector ON resumes USING GIN (search_vector);

CREATE OR REPLACE FUNCTION resumes_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple', COALESCE(NEW.raw_text, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER resumes_search_vector_trigger
    BEFORE INSERT OR UPDATE ON resumes
    FOR EACH ROW EXECUTE FUNCTION resumes_search_vector_update();

CREATE INDEX idx_resumes_source ON resumes(source);
CREATE INDEX idx_resumes_status ON resumes(status);
CREATE INDEX idx_resumes_uploaded_by ON resumes(uploaded_by);
CREATE INDEX idx_resumes_created_at ON resumes(created_at DESC);
