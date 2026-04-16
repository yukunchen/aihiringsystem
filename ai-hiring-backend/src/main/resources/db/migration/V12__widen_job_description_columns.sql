-- Widen education and experience columns from VARCHAR(50) to TEXT.
-- These are free-form text fields where users commonly enter descriptions
-- longer than 50 characters (e.g. "Bachelor's degree or above in Computer Science").
ALTER TABLE job_descriptions ALTER COLUMN education TYPE TEXT;
ALTER TABLE job_descriptions ALTER COLUMN experience TYPE TEXT;
