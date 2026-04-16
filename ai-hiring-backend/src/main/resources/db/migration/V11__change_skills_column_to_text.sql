-- The skills column was defined as JSONB but the application treats it as a plain
-- comma-separated string.  Inserting a non-JSON string into a JSONB column causes
-- a PostgreSQL error, which makes JD creation fail (issue #94).
ALTER TABLE job_descriptions ALTER COLUMN skills TYPE TEXT USING skills::TEXT;
