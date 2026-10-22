-- users.role
ALTER TABLE users
    ADD CONSTRAINT chk_users_role
        CHECK (role IN ('ADMIN', 'MASTER', 'SUB_USER', 'USER'));

-- staged_income.status  (valid values after V5)
ALTER TABLE staged_income
    ADD CONSTRAINT chk_staged_income_status
        CHECK (status IN ('PENDING', 'RETRYING', 'MOVED', 'FAILED'));

-- transactions.status
ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_status
        CHECK (status IN ('DRAFT', 'POSTED', 'VOID'));

-- income.currency length (3 letters)
ALTER TABLE income
    ADD CONSTRAINT chk_income_currency
        CHECK (char_length(currency) = 3);

-- income.amount positive
ALTER TABLE income
    ADD CONSTRAINT chk_income_amount_positive
        CHECK (amount > 0);

-- expense.currency + amount
ALTER TABLE expense
    ADD CONSTRAINT chk_expense_currency
        CHECK (char_length(currency) = 3),
    ADD CONSTRAINT chk_expense_amount_positive
        CHECK (amount > 0);

-- investment.currency + amount
ALTER TABLE investment
    ADD CONSTRAINT chk_investment_currency
        CHECK (char_length(currency) = 3),
    ADD CONSTRAINT chk_investment_amount_positive
        CHECK (amount > 0);

-- staged_income.retry_count never negative
ALTER TABLE staged_income
    ADD CONSTRAINT chk_staged_income_retry_non_negative
        CHECK (retry_count >= 0);