CREATE TABLE income (
                        id                  UUID            PRIMARY KEY,
                        farm_id             UUID            NOT NULL,
                        amount              NUMERIC(19, 4)  NOT NULL,
                        currency            VARCHAR(3)      NOT NULL DEFAULT 'USD',
                        source              VARCHAR(100)    NOT NULL,
                        category            VARCHAR(50),
                        description         VARCHAR(500),
                        income_date         DATE            NOT NULL,
                        created_by_user_id  UUID,
                        created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
                        updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),

                        CONSTRAINT fk_income_farm
                            FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                        CONSTRAINT fk_income_creator
                            FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_income_farm_id     ON income (farm_id);
CREATE INDEX idx_income_income_date ON income (income_date);
CREATE INDEX idx_income_farm_date   ON income (farm_id, income_date);
CREATE INDEX idx_income_creator     ON income (created_by_user_id);

CREATE TABLE staged_income (
                               id                   UUID          PRIMARY KEY,
                               farm_id              UUID          NOT NULL,
                               payload_json         TEXT          NOT NULL,
                               status               VARCHAR(20)   NOT NULL,
                               validation_errors    VARCHAR(2000),
                               submitted_by_user_id UUID,
                               submitted_by_email   VARCHAR(100),
                               submitted_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
                               processed_at         TIMESTAMP,
                               moved_income_id      UUID,
                               retry_count          INT           NOT NULL DEFAULT 0,

                               CONSTRAINT fk_staged_income_farm
                                   FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                               CONSTRAINT fk_staged_income_submitter
                                   FOREIGN KEY (submitted_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                               CONSTRAINT fk_staged_income_moved
                                   FOREIGN KEY (moved_income_id) REFERENCES income (id) ON DELETE SET NULL
);

CREATE INDEX idx_staged_income_status       ON staged_income (status);
CREATE INDEX idx_staged_income_farm_id      ON staged_income (farm_id);
CREATE INDEX idx_staged_income_status_farm  ON staged_income (status, farm_id);