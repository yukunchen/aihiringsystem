CREATE TYPE job_status AS ENUM ('DRAFT', 'PUBLISHED', 'PAUSED', 'CLOSED');

CREATE TABLE job_descriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    requirements TEXT,
    skills JSONB,
    education VARCHAR(50),
    experience VARCHAR(50),
    salary_range VARCHAR(100),
    location VARCHAR(100),
    status job_status NOT NULL DEFAULT 'DRAFT',
    department_id UUID NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_descriptions_status ON job_descriptions(status);
CREATE INDEX idx_job_descriptions_department ON job_descriptions(department_id);
CREATE INDEX idx_job_descriptions_created_by ON job_descriptions(created_by);
CREATE INDEX idx_job_descriptions_created_at ON job_descriptions(created_at DESC);
CREATE INDEX idx_job_descriptions_title ON job_descriptions(title);
