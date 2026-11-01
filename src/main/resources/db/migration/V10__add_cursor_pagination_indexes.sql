

CREATE INDEX IF NOT EXISTS idx_income_cursor
    ON income (farm_id, income_date DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_expense_cursor
    ON expense (farm_id, expense_date DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_transactions_cursor
    ON transactions (farm_id, occurred_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_tasks_cursor
    ON tasks (farm_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_salary_cursor
    ON salary_payments (farm_id, payment_date DESC, id DESC);