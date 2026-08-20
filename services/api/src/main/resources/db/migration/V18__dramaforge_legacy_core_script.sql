-- Legacy job types renamed in application code.
UPDATE dramaforge_jobs
SET job_type = 'GENERATE_SCRIPT'
WHERE job_type = 'CORE_SCRIPT';

UPDATE dramaforge_jobs
SET job_type = 'EXTRACT_ASSETS'
WHERE job_type = 'CORE_BIBLE';
