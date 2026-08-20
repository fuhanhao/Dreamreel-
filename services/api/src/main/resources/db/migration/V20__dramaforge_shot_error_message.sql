-- Persist per-shot failure reason for studio UI.
ALTER TABLE dramaforge_shots
    ADD COLUMN IF NOT EXISTS error_message VARCHAR(2000);
