ALTER TABLE staged_income
    ADD COLUMN last_attempt_at TIMESTAMP,
    ADD COLUMN next_retry_at   TIMESTAMP;

-- Remap legacy terminal status into the new lifecycle
UPDATE staged_income
SET status = 'FAILED'
WHERE status = 'REJECTED';

UPDATE staged_income
SET status = 'RETRYING'
WHERE status = 'VALIDATED';

-- Scheduler picks due rows by (status, next_retry_at)
CREATE INDEX idx_staged_income_due
    ON staged_income (status, next_retry_at);

-- FIFO ordering for the PENDING scan
CREATE INDEX idx_staged_income_status_submitted
    ON staged_income (status, submitted_at);