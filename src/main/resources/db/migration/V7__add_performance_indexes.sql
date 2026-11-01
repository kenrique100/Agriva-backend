
CREATE INDEX IF NOT EXISTS idx_harvests_farm          ON harvests (farm_id);
CREATE INDEX IF NOT EXISTS idx_harvests_cycle         ON harvests (crop_cycle_id);
CREATE INDEX IF NOT EXISTS idx_harvests_farm_date_desc
    ON harvests (farm_id, harvest_date DESC);

-- ---- crop_activities ---------------------------------------------
CREATE INDEX IF NOT EXISTS idx_activities_farm_date
    ON crop_activities (farm_id, activity_date DESC);

-- ---- crops -------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_crops_farm_name ON crops (farm_id, name);

-- ---- income / expense / transactions -----------------------------
-- DESC partial indexes so the paginated lists hit an index-only scan
-- on the live rows (deleted_at IS NULL filter is partial).
CREATE INDEX IF NOT EXISTS idx_income_farm_date_desc
    ON income (farm_id, income_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_expense_farm_date_desc
    ON expense (farm_id, expense_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_transactions_farm_occurred_desc
    ON transactions (farm_id, occurred_at DESC) WHERE deleted_at IS NULL;

-- ---- salary_payments ---------------------------------------------
CREATE INDEX IF NOT EXISTS idx_sp_farm_personnel_date
    ON salary_payments (farm_id, personnel_id, payment_date DESC);

-- ---- tasks -------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_tasks_farm_status_live
    ON tasks (farm_id, status) WHERE deleted_at IS NULL;

-- ---- live-only farm scans ----------------------------------------
CREATE INDEX IF NOT EXISTS idx_income_live
    ON income (farm_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_expense_live
    ON expense (farm_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_transactions_live
    ON transactions (farm_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_investment_live
    ON investment (farm_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_crop_cycles_live
    ON crop_cycles (farm_id) WHERE deleted_at IS NULL;

-- ---- staging due-scan (partial — excludes MOVED/FAILED) ----------
CREATE INDEX IF NOT EXISTS idx_staged_income_live_due
    ON staged_income (next_retry_at) WHERE status IN ('PENDING','RETRYING');
CREATE INDEX IF NOT EXISTS idx_staged_expense_live_due
    ON staged_expense (next_retry_at) WHERE status IN ('PENDING','RETRYING');
CREATE INDEX IF NOT EXISTS idx_staged_task_live_due
    ON staged_task (next_retry_at) WHERE status IN ('PENDING','RETRYING');
CREATE INDEX IF NOT EXISTS idx_staged_salary_live_due
    ON staged_salary (next_retry_at) WHERE status IN ('PENDING','RETRYING');
CREATE INDEX IF NOT EXISTS idx_staged_crop_live_due
    ON staged_crop_cycle (next_retry_at) WHERE status IN ('PENDING','RETRYING');