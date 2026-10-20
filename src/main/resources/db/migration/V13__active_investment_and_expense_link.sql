-- 1. Active flag on investment
ALTER TABLE investment ADD COLUMN active BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE investment ADD COLUMN activated_at TIMESTAMP WITH TIME ZONE;

-- 2. At most ONE active investment per farm
CREATE UNIQUE INDEX uq_investment_one_active_per_farm
    ON investment(farm_id) WHERE active = TRUE;

-- 3. Link each expense to the investment that funded it
ALTER TABLE expense ADD COLUMN investment_id UUID;
ALTER TABLE expense ADD CONSTRAINT fk_expense_investment
    FOREIGN KEY (investment_id) REFERENCES investment(id) ON DELETE SET NULL;
CREATE INDEX idx_expense_investment_id ON expense(investment_id);