
CREATE INDEX idx_income_creator_live       ON income        (farm_id, created_by_user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_expense_creator_live      ON expense       (farm_id, created_by_user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_investment_creator_live   ON investment    (farm_id, created_by_user_id) WHERE deleted_at IS NULL;

CREATE INDEX idx_income_farm_date_desc        ON income        (farm_id, income_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_expense_farm_date_desc       ON expense       (farm_id, expense_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_transactions_farm_occurred_desc ON transactions (farm_id, occurred_at DESC) WHERE deleted_at IS NULL;

CREATE INDEX idx_income_cursor       ON income        (farm_id, income_date DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_expense_cursor      ON expense       (farm_id, expense_date DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_transactions_cursor ON transactions  (farm_id, occurred_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_tasks_cursor        ON tasks         (farm_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_salary_cursor       ON salary_payments (farm_id, payment_date DESC, id DESC);

CREATE INDEX idx_income_live       ON income        (farm_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_expense_live      ON expense       (farm_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_transactions_live ON transactions  (farm_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_investment_live   ON investment    (farm_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_crop_cycles_live  ON crop_cycles   (farm_id) WHERE deleted_at IS NULL;

CREATE INDEX idx_harvests_farm           ON harvests (farm_id);
CREATE INDEX idx_harvests_cycle          ON harvests (crop_cycle_id);
CREATE INDEX idx_harvests_farm_date_desc ON harvests (farm_id, harvest_date DESC);
CREATE INDEX idx_activities_farm_date    ON crop_activities (farm_id, activity_date DESC);
CREATE INDEX idx_crops_farm_name         ON crops (farm_id, name);

CREATE INDEX idx_tasks_farm_status_live ON tasks (farm_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_sp_farm_personnel_date ON salary_payments (farm_id, personnel_id, payment_date DESC);