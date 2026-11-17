

CREATE TABLE staged_income (
                               id                   UUID NOT NULL,
                               farm_id              UUID NOT NULL,
                               payload_json         TEXT NOT NULL,
                               status               VARCHAR(20) NOT NULL,
                               validation_errors    VARCHAR(2000),
                               submitted_by_user_id UUID,
                               submitted_by_email   VARCHAR(100),
                               submitted_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                               last_attempt_at      TIMESTAMP,
                               next_retry_at        TIMESTAMP,
                               processed_at         TIMESTAMP,
                               moved_income_id      UUID,
                               retry_count          INT NOT NULL DEFAULT 0,
                               version              BIGINT NOT NULL DEFAULT 0,
                               PRIMARY KEY (id, submitted_at),
                               CONSTRAINT fk_staged_income_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                               CONSTRAINT fk_staged_income_submitter FOREIGN KEY (submitted_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                               CONSTRAINT fk_staged_income_moved FOREIGN KEY (moved_income_id) REFERENCES income (id) ON DELETE SET NULL,
                               CONSTRAINT chk_staged_income_status CHECK (status IN ('PENDING','RETRYING','MOVED','FAILED')),
                               CONSTRAINT chk_staged_income_retry_non_negative CHECK (retry_count >= 0)
) PARTITION BY RANGE (submitted_at);
CREATE INDEX idx_staged_income_status   ON staged_income (status);
CREATE INDEX idx_staged_income_farm_id  ON staged_income (farm_id);
CREATE INDEX idx_staged_income_due      ON staged_income (status, next_retry_at);
CREATE INDEX idx_staged_income_live_due ON staged_income (next_retry_at)
    WHERE status IN ('PENDING','RETRYING');

CREATE TABLE staged_expense (
                                id                   UUID NOT NULL,
                                farm_id              UUID NOT NULL,
                                payload_json         TEXT NOT NULL,
                                status               VARCHAR(20) NOT NULL,
                                validation_errors    VARCHAR(2000),
                                submitted_by_user_id UUID,
                                submitted_by_email   VARCHAR(100),
                                submitted_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                                last_attempt_at      TIMESTAMP,
                                next_retry_at        TIMESTAMP,
                                processed_at         TIMESTAMP,
                                moved_expense_id     UUID,
                                retry_count          INT NOT NULL DEFAULT 0,
                                version              BIGINT NOT NULL DEFAULT 0,
                                PRIMARY KEY (id, submitted_at),
                                CONSTRAINT fk_staged_expense_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                CONSTRAINT fk_staged_expense_submitter FOREIGN KEY (submitted_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                                CONSTRAINT fk_staged_expense_moved FOREIGN KEY (moved_expense_id) REFERENCES expense (id) ON DELETE SET NULL,
                                CONSTRAINT chk_staged_expense_status CHECK (status IN ('PENDING','RETRYING','MOVED','FAILED')),
                                CONSTRAINT chk_staged_expense_retry_non_negative CHECK (retry_count >= 0)
) PARTITION BY RANGE (submitted_at);
CREATE INDEX idx_staged_expense_status   ON staged_expense (status);
CREATE INDEX idx_staged_expense_farm_id  ON staged_expense (farm_id);
CREATE INDEX idx_staged_expense_due      ON staged_expense (status, next_retry_at);
CREATE INDEX idx_staged_expense_live_due ON staged_expense (next_retry_at)
    WHERE status IN ('PENDING','RETRYING');

CREATE TABLE staged_task (
                             id                   UUID NOT NULL,
                             farm_id              UUID NOT NULL,
                             payload_json         TEXT NOT NULL,
                             status               VARCHAR(20) NOT NULL,
                             validation_errors    VARCHAR(2000),
                             submitted_by_user_id UUID,
                             submitted_by_email   VARCHAR(100),
                             submitted_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                             last_attempt_at      TIMESTAMP,
                             next_retry_at        TIMESTAMP,
                             processed_at         TIMESTAMP,
                             moved_task_id        UUID,
                             retry_count          INT NOT NULL DEFAULT 0,
                             version              BIGINT NOT NULL DEFAULT 0,
                             PRIMARY KEY (id, submitted_at),
                             CONSTRAINT fk_staged_task_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                             CONSTRAINT fk_staged_task_submitter FOREIGN KEY (submitted_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                             CONSTRAINT fk_staged_task_moved FOREIGN KEY (moved_task_id) REFERENCES tasks (id) ON DELETE SET NULL,
                             CONSTRAINT chk_staged_task_status CHECK (status IN ('PENDING','RETRYING','MOVED','FAILED')),
                             CONSTRAINT chk_staged_task_retry_non_negative CHECK (retry_count >= 0)
) PARTITION BY RANGE (submitted_at);
CREATE INDEX idx_staged_task_status   ON staged_task (status);
CREATE INDEX idx_staged_task_farm_id  ON staged_task (farm_id);
CREATE INDEX idx_staged_task_due      ON staged_task (status, next_retry_at);
CREATE INDEX idx_staged_task_live_due ON staged_task (next_retry_at)
    WHERE status IN ('PENDING','RETRYING');

CREATE TABLE staged_salary (
                               id                   UUID NOT NULL,
                               farm_id              UUID NOT NULL,
                               payload_json         TEXT NOT NULL,
                               status               VARCHAR(20) NOT NULL,
                               validation_errors    VARCHAR(2000),
                               submitted_by_user_id UUID,
                               submitted_by_email   VARCHAR(100),
                               submitted_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                               last_attempt_at      TIMESTAMP,
                               next_retry_at        TIMESTAMP,
                               processed_at         TIMESTAMP,
                               moved_salary_id      UUID,
                               retry_count          INT NOT NULL DEFAULT 0,
                               version              BIGINT NOT NULL DEFAULT 0,
                               PRIMARY KEY (id, submitted_at),
                               CONSTRAINT fk_staged_salary_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                               CONSTRAINT fk_staged_salary_submitter FOREIGN KEY (submitted_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                               CONSTRAINT fk_staged_salary_moved FOREIGN KEY (moved_salary_id) REFERENCES salary_payments (id) ON DELETE SET NULL,
                               CONSTRAINT chk_staged_salary_status CHECK (status IN ('PENDING','RETRYING','MOVED','FAILED')),
                               CONSTRAINT chk_staged_salary_retry_non_negative CHECK (retry_count >= 0)
) PARTITION BY RANGE (submitted_at);
CREATE INDEX idx_staged_salary_status   ON staged_salary (status);
CREATE INDEX idx_staged_salary_farm_id  ON staged_salary (farm_id);
CREATE INDEX idx_staged_salary_due      ON staged_salary (status, next_retry_at);
CREATE INDEX idx_staged_salary_live_due ON staged_salary (next_retry_at)
    WHERE status IN ('PENDING','RETRYING');

DO $$
BEGIN
    PERFORM ensure_partitions('staged_income',  3, 2);
    PERFORM ensure_partitions('staged_expense', 3, 2);
    PERFORM ensure_partitions('staged_task',    3, 2);
    PERFORM ensure_partitions('staged_salary',  3, 2);
END $$;