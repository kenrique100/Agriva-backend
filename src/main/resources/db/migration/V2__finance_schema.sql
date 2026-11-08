CREATE TABLE transactions (
                              id                 UUID PRIMARY KEY,
                              farm_id            UUID         NOT NULL,
                              transaction_ref    VARCHAR(50)  NOT NULL,
                              occurred_at        TIMESTAMP    NOT NULL,
                              status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
                              notes              TEXT,
                              created_by_user_id UUID,
                              deleted_at         TIMESTAMPTZ,
                              created_by         VARCHAR(50),
                              updated_by         VARCHAR(50),
                              created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
                              updated_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
                              version            BIGINT       NOT NULL DEFAULT 0,

                              CONSTRAINT fk_transactions_farm
                                  FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                              CONSTRAINT fk_transactions_creator
                                  FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                              CONSTRAINT chk_transactions_status
                                  CHECK (status IN ('DRAFT','POSTED','VOID'))
);

CREATE INDEX idx_transactions_farm     ON transactions (farm_id);
CREATE INDEX idx_transactions_occurred ON transactions (occurred_at);
CREATE INDEX idx_transactions_status   ON transactions (status);
CREATE UNIQUE INDEX ux_transactions_farm_ref
    ON transactions (farm_id, transaction_ref) WHERE deleted_at IS NULL;

CREATE TABLE investment (
                            id                 UUID PRIMARY KEY,
                            farm_id            UUID          NOT NULL,
                            transaction_id     UUID,
                            amount             NUMERIC(19,4) NOT NULL,
                            currency           VARCHAR(3)    NOT NULL DEFAULT 'USD',
                            investor_name      VARCHAR(100)  NOT NULL,
                            type               VARCHAR(50),
                            description        VARCHAR(500),
                            investment_date    DATE          NOT NULL,
                            created_by_user_id UUID,
                            active             BOOLEAN       NOT NULL DEFAULT FALSE,
                            activated_at       TIMESTAMP,
                            deleted_at         TIMESTAMPTZ,
                            created_by         VARCHAR(50),
                            updated_by         VARCHAR(50),
                            created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
                            updated_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
                            version            BIGINT        NOT NULL DEFAULT 0,

                            CONSTRAINT fk_investment_farm
                                FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                            CONSTRAINT fk_investment_creator
                                FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                            CONSTRAINT fk_investment_transaction
                                FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE SET NULL,
                            CONSTRAINT chk_investment_currency
                                CHECK (char_length(currency) = 3),
                            CONSTRAINT chk_investment_amount_positive
                                CHECK (amount > 0)
);

CREATE INDEX idx_investment_farm_id         ON investment (farm_id);
CREATE INDEX idx_investment_investment_date ON investment (investment_date);
CREATE INDEX idx_investment_farm_date       ON investment (farm_id, investment_date);
CREATE INDEX idx_investment_creator         ON investment (created_by_user_id);
CREATE INDEX idx_investment_transaction     ON investment (transaction_id);
CREATE UNIQUE INDEX uq_investment_one_active_per_farm
    ON investment (farm_id) WHERE active = TRUE;
CREATE INDEX idx_investment_active
    ON investment (farm_id) WHERE active = TRUE AND deleted_at IS NULL;

CREATE TABLE expense (
                         id                 UUID PRIMARY KEY,
                         farm_id            UUID          NOT NULL,
                         transaction_id     UUID,
                         investment_id      UUID,
                         reason             VARCHAR(255)  NOT NULL,
                         expense_date       DATE          NOT NULL,
                         qty_purchased      INT           NOT NULL DEFAULT 1,
                         amount_paid        NUMERIC(19,4) NOT NULL,
                         expected_amount    NUMERIC(19,4) NOT NULL DEFAULT 0,
                         due_balance        NUMERIC(19,4) NOT NULL DEFAULT 0,
                         receipt            VARCHAR(255),
                         created_by_user_id UUID,
                         currency           VARCHAR(3)    NOT NULL DEFAULT 'XAF',
                         category           VARCHAR(50)   NOT NULL,
                         deleted_at         TIMESTAMPTZ,
                         created_by         VARCHAR(50),
                         updated_by         VARCHAR(50),
                         created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
                         updated_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
                         version            BIGINT        NOT NULL DEFAULT 0,

                         CONSTRAINT fk_expense_farm
                             FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                         CONSTRAINT fk_expense_creator
                             FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                         CONSTRAINT fk_expense_transaction
                             FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE SET NULL,
                         CONSTRAINT fk_expense_investment
                             FOREIGN KEY (investment_id) REFERENCES investment (id) ON DELETE SET NULL,
                         CONSTRAINT chk_expense_currency
                             CHECK (char_length(currency) = 3),
                         CONSTRAINT chk_expense_amount_positive
                             CHECK (amount_paid > 0)
);

CREATE INDEX idx_expense_farm_id       ON expense (farm_id);
CREATE INDEX idx_expense_expense_date  ON expense (expense_date);
CREATE INDEX idx_expense_farm_date     ON expense (farm_id, expense_date);
CREATE INDEX idx_expense_creator       ON expense (created_by_user_id);
CREATE INDEX idx_expense_transaction   ON expense (transaction_id);
CREATE INDEX idx_expense_investment_id ON expense (investment_id);

CREATE TABLE income (
                        id                 UUID PRIMARY KEY,
                        farm_id            UUID          NOT NULL,
                        transaction_id     UUID,
                        reason             VARCHAR(255)  NOT NULL,
                        income_date        DATE          NOT NULL,
                        quantity           INT           NOT NULL DEFAULT 1,
                        amount_received    NUMERIC(19,4) NOT NULL,
                        expected_amount    NUMERIC(19,4) NOT NULL DEFAULT 0,
                        due_balance        NUMERIC(19,4) NOT NULL DEFAULT 0,
                        receipt            VARCHAR(255),
                        created_by_user_id UUID,
                        currency           VARCHAR(3)    NOT NULL DEFAULT 'XAF',
                        category           VARCHAR(50)   NOT NULL,
                        deleted_at         TIMESTAMPTZ,
                        created_by         VARCHAR(50),
                        updated_by         VARCHAR(50),
                        created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
                        updated_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
                        version            BIGINT        NOT NULL DEFAULT 0,

                        CONSTRAINT fk_income_farm
                            FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                        CONSTRAINT fk_income_creator
                            FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                        CONSTRAINT fk_income_transaction
                            FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE SET NULL,
                        CONSTRAINT chk_income_currency
                            CHECK (char_length(currency) = 3),
                        CONSTRAINT chk_income_amount_positive
                            CHECK (amount_received > 0)
);

CREATE INDEX idx_income_farm_id     ON income (farm_id);
CREATE INDEX idx_income_income_date ON income (income_date);
CREATE INDEX idx_income_farm_date   ON income (farm_id, income_date);
CREATE INDEX idx_income_creator     ON income (created_by_user_id);
CREATE INDEX idx_income_transaction ON income (transaction_id);

CREATE TABLE salary_payments (
                                 id                 UUID PRIMARY KEY,
                                 farm_id            UUID          NOT NULL,
                                 personnel_id       UUID          NOT NULL,
                                 amount             NUMERIC(19,4) NOT NULL,
                                 payment_date       DATE          NOT NULL,
                                 status             VARCHAR(20)   NOT NULL DEFAULT 'PROCESSED',
                                 payment_reference  VARCHAR(50)   NOT NULL,
                                 expense_id         UUID,
                                 paid_by_user_id    UUID,
                                 created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),

                                 CONSTRAINT fk_sp_farm
                                     FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                 CONSTRAINT fk_sp_personnel
                                     FOREIGN KEY (personnel_id) REFERENCES personnel (id) ON DELETE CASCADE,
                                 CONSTRAINT fk_sp_expense
                                     FOREIGN KEY (expense_id) REFERENCES expense (id) ON DELETE SET NULL,
                                 CONSTRAINT fk_sp_paid_by
                                     FOREIGN KEY (paid_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                                 CONSTRAINT ux_salary_payments_ref UNIQUE (payment_reference),
                                 CONSTRAINT chk_sp_status
                                     CHECK (status IN ('PENDING','PROCESSED','FAILED','CANCELLED')),
                                 CONSTRAINT chk_sp_amount_positive
                                     CHECK (amount > 0)
);

CREATE INDEX idx_sp_farm      ON salary_payments (farm_id);
CREATE INDEX idx_sp_personnel ON salary_payments (personnel_id);
CREATE INDEX idx_sp_date      ON salary_payments (payment_date);
CREATE INDEX idx_sp_expense   ON salary_payments (expense_id);