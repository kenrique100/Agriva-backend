CREATE TABLE staged_expense (
    id                   UUID          PRIMARY KEY,
    farm_id              UUID          NOT NULL,
    payload_json         TEXT          NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    validation_errors    VARCHAR(2000),
    submitted_by_user_id UUID,
    submitted_by_email   VARCHAR(100),
    submitted_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    last_attempt_at      TIMESTAMP,
    next_retry_at        TIMESTAMP,
    processed_at         TIMESTAMP,
    moved_expense_id     UUID,
    retry_count          INT           NOT NULL DEFAULT 0,

    CONSTRAINT fk_staged_expense_farm
        FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
    CONSTRAINT fk_staged_expense_submitter
        FOREIGN KEY (submitted_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_staged_expense_moved
        FOREIGN KEY (moved_expense_id) REFERENCES expense (id) ON DELETE SET NULL,
    CONSTRAINT chk_staged_expense_status
        CHECK (status IN ('PENDING', 'RETRYING', 'MOVED', 'FAILED')),
    CONSTRAINT chk_staged_expense_retry_non_negative
        CHECK (retry_count >= 0)
);

CREATE INDEX idx_staged_expense_status         ON staged_expense (status);
CREATE INDEX idx_staged_expense_farm_id        ON staged_expense (farm_id);
CREATE INDEX idx_staged_expense_status_farm    ON staged_expense (status, farm_id);
CREATE INDEX idx_staged_expense_due            ON staged_expense (status, next_retry_at);