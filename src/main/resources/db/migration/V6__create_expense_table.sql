CREATE TABLE expense (
                         id                  UUID            PRIMARY KEY,
                         farm_id             UUID            NOT NULL,
                         transaction_id      UUID,
                         amount              NUMERIC(19, 4)  NOT NULL,
                         currency            VARCHAR(3)      NOT NULL DEFAULT 'USD',
                         category            VARCHAR(50),
                         vendor              VARCHAR(100),
                         description         VARCHAR(500),
                         expense_date        DATE            NOT NULL,
                         created_by_user_id  UUID,
                         created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
                         updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),

                         CONSTRAINT fk_expense_farm
                             FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                         CONSTRAINT fk_expense_creator
                             FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                         CONSTRAINT fk_expense_transaction
                             FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE SET NULL
);

CREATE INDEX idx_expense_farm_id        ON expense (farm_id);
CREATE INDEX idx_expense_expense_date   ON expense (expense_date);
CREATE INDEX idx_expense_farm_date      ON expense (farm_id, expense_date);
CREATE INDEX idx_expense_creator        ON expense (created_by_user_id);
CREATE INDEX idx_expense_transaction    ON expense (transaction_id);