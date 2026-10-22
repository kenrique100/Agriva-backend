CREATE TABLE transactions (
                              id                  UUID         PRIMARY KEY,
                              farm_id             UUID         NOT NULL,
                              transaction_ref     VARCHAR(50)  NOT NULL,
                              occurred_at         TIMESTAMP    NOT NULL,
                              status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
                              notes               TEXT,
                              created_by_user_id  UUID,
                              created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
                              updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

                              CONSTRAINT fk_transactions_farm
                                  FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                              CONSTRAINT fk_transactions_creator
                                  FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_transactions_farm     ON transactions (farm_id);
CREATE INDEX idx_transactions_occurred ON transactions (occurred_at);
CREATE INDEX idx_transactions_status   ON transactions (status);

-- Human-readable ref is unique per farm.
CREATE UNIQUE INDEX ux_transactions_farm_ref
    ON transactions (farm_id, transaction_ref);

ALTER TABLE income
    ADD COLUMN transaction_id UUID;

ALTER TABLE income
    ADD CONSTRAINT fk_income_transaction
        FOREIGN KEY (transaction_id) REFERENCES transactions (id)
            ON DELETE SET NULL;

CREATE INDEX idx_income_transaction ON income (transaction_id);