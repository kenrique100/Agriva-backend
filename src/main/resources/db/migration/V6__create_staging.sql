

-- staged_income
CREATE TABLE staged_income (
                               id                    UUID          PRIMARY KEY,
                               farm_id               UUID          NOT NULL,
                               payload_json          TEXT          NOT NULL,
                               status                VARCHAR(20)   NOT NULL,
                               validation_errors     VARCHAR(2000),
                               submitted_by_user_id  UUID,
                               submitted_by_email    VARCHAR(100),
                               submitted_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
                               last_attempt_at       TIMESTAMP,
                               next_retry_at         TIMESTAMP,
                               processed_at          TIMESTAMP,
                               moved_income_id       UUID,
                               retry_count           INT           NOT NULL DEFAULT 0,

                               CONSTRAINT fk_staged_income_farm
                                   FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                               CONSTRAINT fk_staged_income_submitter
                                   FOREIGN KEY (submitted_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                               CONSTRAINT fk_staged_income_moved
                                   FOREIGN KEY (moved_income_id) REFERENCES income (id) ON DELETE SET NULL,
                               CONSTRAINT chk_staged_income_status
                                   CHECK (status IN ('PENDING','RETRYING','MOVED','FAILED')),
                               CONSTRAINT chk_staged_income_retry_non_negative
                                   CHECK (retry_count >= 0)
);

CREATE INDEX idx_staged_income_status         ON staged_income (status);
CREATE INDEX idx_staged_income_farm_id        ON staged_income (farm_id);
CREATE INDEX idx_staged_income_status_farm    ON staged_income (status, farm_id);
CREATE INDEX idx_staged_income_due            ON staged_income (status, next_retry_at);
CREATE INDEX idx_staged_income_status_submitted
    ON staged_income (status, submitted_at);

-- staged_expense
CREATE TABLE staged_expense (
                                id                    UUID          PRIMARY KEY,
                                farm_id               UUID          NOT NULL,
                                payload_json          TEXT          NOT NULL,
                                status                VARCHAR(20)   NOT NULL,
                                validation_errors     VARCHAR(2000),
                                submitted_by_user_id  UUID,
                                submitted_by_email    VARCHAR(100),
                                submitted_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
                                last_attempt_at       TIMESTAMP,
                                next_retry_at         TIMESTAMP,
                                processed_at          TIMESTAMP,
                                moved_expense_id      UUID,
                                retry_count           INT           NOT NULL DEFAULT 0,

                                CONSTRAINT fk_staged_expense_farm
                                    FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                CONSTRAINT fk_staged_expense_submitter
                                    FOREIGN KEY (submitted_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                                CONSTRAINT fk_staged_expense_moved
                                    FOREIGN KEY (moved_expense_id) REFERENCES expense (id) ON DELETE SET NULL,
                                CONSTRAINT chk_staged_expense_status
                                    CHECK (status IN ('PENDING','RETRYING','MOVED','FAILED')),
                                CONSTRAINT chk_staged_expense_retry_non_negative
                                    CHECK (retry_count >= 0)
);

CREATE INDEX idx_staged_expense_status        ON staged_expense (status);
CREATE INDEX idx_staged_expense_farm_id       ON staged_expense (farm_id);
CREATE INDEX idx_staged_expense_status_farm   ON staged_expense (status, farm_id);
CREATE INDEX idx_staged_expense_due           ON staged_expense (status, next_retry_at);

-- staged_task
CREATE TABLE staged_task (
                             id                    UUID          PRIMARY KEY,
                             farm_id               UUID          NOT NULL,
                             payload_json          TEXT          NOT NULL,
                             status                VARCHAR(20)   NOT NULL,
                             validation_errors     VARCHAR(2000),
                             submitted_by_user_id  UUID,
                             submitted_by_email    VARCHAR(100),
                             submitted_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
                             last_attempt_at       TIMESTAMP,
                             next_retry_at         TIMESTAMP,
                             processed_at          TIMESTAMP,
                             moved_task_id         UUID,
                             retry_count           INT           NOT NULL DEFAULT 0,

                             CONSTRAINT fk_staged_task_farm
                                 FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                             CONSTRAINT fk_staged_task_submitter
                                 FOREIGN KEY (submitted_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                             CONSTRAINT fk_staged_task_moved
                                 FOREIGN KEY (moved_task_id) REFERENCES tasks (id) ON DELETE SET NULL,
                             CONSTRAINT chk_staged_task_status
                                 CHECK (status IN ('PENDING','RETRYING','MOVED','FAILED')),
                             CONSTRAINT chk_staged_task_retry_non_negative
                                 CHECK (retry_count >= 0)
);

CREATE INDEX idx_staged_task_status        ON staged_task (status);
CREATE INDEX idx_staged_task_farm_id       ON staged_task (farm_id);
CREATE INDEX idx_staged_task_status_farm   ON staged_task (status, farm_id);
CREATE INDEX idx_staged_task_due           ON staged_task (status, next_retry_at);

-- staged_salary
CREATE TABLE staged_salary (
                               id                    UUID          PRIMARY KEY,
                               farm_id               UUID          NOT NULL,
                               payload_json          TEXT          NOT NULL,
                               status                VARCHAR(20)   NOT NULL,
                               validation_errors     VARCHAR(2000),
                               submitted_by_user_id  UUID,
                               submitted_by_email    VARCHAR(100),
                               submitted_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
                               last_attempt_at       TIMESTAMP,
                               next_retry_at         TIMESTAMP,
                               processed_at          TIMESTAMP,
                               moved_salary_id       UUID,
                               retry_count           INT           NOT NULL DEFAULT 0,

                               CONSTRAINT fk_staged_salary_farm
                                   FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                               CONSTRAINT fk_staged_salary_submitter
                                   FOREIGN KEY (submitted_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                               CONSTRAINT fk_staged_salary_moved
                                   FOREIGN KEY (moved_salary_id) REFERENCES salary_payments (id) ON DELETE SET NULL,
                               CONSTRAINT chk_staged_salary_status
                                   CHECK (status IN ('PENDING','RETRYING','MOVED','FAILED')),
                               CONSTRAINT chk_staged_salary_retry_non_negative
                                   CHECK (retry_count >= 0)
);

CREATE INDEX idx_staged_salary_status        ON staged_salary (status);
CREATE INDEX idx_staged_salary_farm_id       ON staged_salary (farm_id);
CREATE INDEX idx_staged_salary_status_farm   ON staged_salary (status, farm_id);
CREATE INDEX idx_staged_salary_due           ON staged_salary (status, next_retry_at);

-- staged_crop_cycle
CREATE TABLE staged_crop_cycle (
                                   id                    UUID          PRIMARY KEY,
                                   farm_id               UUID          NOT NULL,
                                   payload_json          TEXT          NOT NULL,
                                   status                VARCHAR(20)   NOT NULL,
                                   validation_errors     VARCHAR(2000),
                                   submitted_by_user_id  UUID,
                                   submitted_by_email    VARCHAR(100),
                                   submitted_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
                                   last_attempt_at       TIMESTAMP,
                                   next_retry_at         TIMESTAMP,
                                   processed_at          TIMESTAMP,
                                   moved_cycle_id        UUID,
                                   retry_count           INT           NOT NULL DEFAULT 0,

                                   CONSTRAINT fk_staged_crop_farm
                                       FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                   CONSTRAINT chk_staged_crop_status
                                       CHECK (status IN ('PENDING','RETRYING','MOVED','FAILED'))
);

CREATE INDEX idx_staged_crop_due ON staged_crop_cycle (status, next_retry_at);

CREATE TABLE consumer_processed_events (
                                           event_id        UUID          PRIMARY KEY,
                                           consumer_group  VARCHAR(100)  NOT NULL,
                                           processed_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_consumer_processed_at
    ON consumer_processed_events (processed_at);