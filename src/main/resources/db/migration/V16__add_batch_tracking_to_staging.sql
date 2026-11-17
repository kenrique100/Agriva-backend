

ALTER TABLE staged_income
    ADD COLUMN batch_id   UUID,
    ADD COLUMN row_number INTEGER;

ALTER TABLE staged_expense
    ADD COLUMN batch_id   UUID,
    ADD COLUMN row_number INTEGER;

-- Lookup path: "give me all rows of batch X for farm Y".
CREATE INDEX idx_staged_income_batch
    ON staged_income (farm_id, batch_id)
    WHERE batch_id IS NOT NULL;

CREATE INDEX idx_staged_expense_batch
    ON staged_expense (farm_id, batch_id)
    WHERE batch_id IS NOT NULL;

COMMENT ON COLUMN staged_income.batch_id    IS 'Groups rows from one bulk upload; NULL for single-submit.';
COMMENT ON COLUMN staged_income.row_number  IS 'Original 1-based Excel row (header row excluded); NULL for single-submit.';
COMMENT ON COLUMN staged_expense.batch_id   IS 'Groups rows from one bulk upload; NULL for single-submit.';
COMMENT ON COLUMN staged_expense.row_number IS 'Original 1-based Excel row (header row excluded); NULL for single-submit.';