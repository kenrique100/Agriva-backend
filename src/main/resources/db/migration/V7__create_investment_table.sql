CREATE TABLE investment (
                            id                  UUID            PRIMARY KEY,
                            farm_id             UUID            NOT NULL,
                            transaction_id      UUID,
                            amount              NUMERIC(19, 4)  NOT NULL,
                            currency            VARCHAR(3)      NOT NULL DEFAULT 'USD',
                            investor_name       VARCHAR(100)    NOT NULL,
                            type                VARCHAR(50),
                            description         VARCHAR(500),
                            investment_date     DATE            NOT NULL,
                            created_by_user_id  UUID,
                            created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
                            updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),

                            CONSTRAINT fk_investment_farm
                                FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                            CONSTRAINT fk_investment_creator
                                FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                            CONSTRAINT fk_investment_transaction
                                FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE SET NULL
);

CREATE INDEX idx_investment_farm_id           ON investment (farm_id);
CREATE INDEX idx_investment_investment_date   ON investment (investment_date);
CREATE INDEX idx_investment_farm_date         ON investment (farm_id, investment_date);
CREATE INDEX idx_investment_creator           ON investment (created_by_user_id);
CREATE INDEX idx_investment_transaction       ON investment (transaction_id);