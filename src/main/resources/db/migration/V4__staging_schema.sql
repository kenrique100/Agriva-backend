CREATE OR REPLACE FUNCTION ensure_partitions(
    parent_table TEXT,
    months_ahead INTEGER DEFAULT 3,
    months_back  INTEGER DEFAULT 0
) RETURNS void AS $$
DECLARE
m            INTEGER;
    start_date   DATE;
    end_date     DATE;
    partition_nm TEXT;
BEGIN
FOR m IN (-months_back)..months_ahead LOOP
        start_date   := DATE_TRUNC('month', CURRENT_DATE + (m || ' months')::INTERVAL)::DATE;
        end_date     := (start_date + INTERVAL '1 month')::DATE;
        partition_nm := parent_table || '_' || TO_CHAR(start_date, 'YYYY_MM');
EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
        partition_nm, parent_table, start_date, end_date
        );
END LOOP;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION drop_partitions_older_than(
    parent_table TEXT,
    keep_days    INTEGER
) RETURNS INTEGER AS $$
DECLARE
partition_rec RECORD;
    cutoff        DATE;
    dropped       INTEGER := 0;
    p_date        DATE;
    suffix        TEXT;
BEGIN
    cutoff := CURRENT_DATE - keep_days;
FOR partition_rec IN
SELECT inhrelid::regclass::text AS name
FROM pg_inherits
WHERE inhparent = parent_table::regclass
    LOOP
        suffix := substring(partition_rec.name FROM '(\d{4}_\d{2})$');
CONTINUE WHEN suffix IS NULL;
BEGIN
            p_date := TO_DATE(suffix, 'YYYY_MM');
EXCEPTION WHEN OTHERS THEN
            CONTINUE;
END;
        IF p_date < cutoff THEN
            EXECUTE format('DROP TABLE IF EXISTS %s', partition_rec.name);
            dropped := dropped + 1;
END IF;
END LOOP;
RETURN dropped;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE staged_income (
                               id                    UUID          NOT NULL,
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
                               PRIMARY KEY (id, submitted_at),
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
) PARTITION BY RANGE (submitted_at);

CREATE INDEX idx_staged_income_status  ON staged_income (status);
CREATE INDEX idx_staged_income_farm_id ON staged_income (farm_id);
CREATE INDEX idx_staged_income_due     ON staged_income (status, next_retry_at);
CREATE INDEX idx_staged_income_live_due
    ON staged_income (next_retry_at) WHERE status IN ('PENDING','RETRYING');

CREATE TABLE staged_expense (
                                id                    UUID          NOT NULL,
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
                                PRIMARY KEY (id, submitted_at),
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
) PARTITION BY RANGE (submitted_at);

CREATE INDEX idx_staged_expense_status  ON staged_expense (status);
CREATE INDEX idx_staged_expense_farm_id ON staged_expense (farm_id);
CREATE INDEX idx_staged_expense_due     ON staged_expense (status, next_retry_at);
CREATE INDEX idx_staged_expense_live_due
    ON staged_expense (next_retry_at) WHERE status IN ('PENDING','RETRYING');

CREATE TABLE staged_task (
                             id                    UUID          NOT NULL,
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
                             PRIMARY KEY (id, submitted_at),
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
) PARTITION BY RANGE (submitted_at);

CREATE INDEX idx_staged_task_status  ON staged_task (status);
CREATE INDEX idx_staged_task_farm_id ON staged_task (farm_id);
CREATE INDEX idx_staged_task_due     ON staged_task (status, next_retry_at);
CREATE INDEX idx_staged_task_live_due
    ON staged_task (next_retry_at) WHERE status IN ('PENDING','RETRYING');

CREATE TABLE staged_salary (
                               id                    UUID          NOT NULL,
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
                               PRIMARY KEY (id, submitted_at),
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
) PARTITION BY RANGE (submitted_at);

CREATE INDEX idx_staged_salary_status  ON staged_salary (status);
CREATE INDEX idx_staged_salary_farm_id ON staged_salary (farm_id);
CREATE INDEX idx_staged_salary_due     ON staged_salary (status, next_retry_at);
CREATE INDEX idx_staged_salary_live_due
    ON staged_salary (next_retry_at) WHERE status IN ('PENDING','RETRYING');

CREATE TABLE staged_crop_cycle (
                                   id                    UUID          NOT NULL,
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
                                   PRIMARY KEY (id, submitted_at),
                                   CONSTRAINT fk_staged_crop_farm
                                       FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                   CONSTRAINT chk_staged_crop_status
                                       CHECK (status IN ('PENDING','RETRYING','MOVED','FAILED'))
) PARTITION BY RANGE (submitted_at);

CREATE INDEX idx_staged_crop_due ON staged_crop_cycle (status, next_retry_at);
CREATE INDEX idx_staged_crop_live_due
    ON staged_crop_cycle (next_retry_at) WHERE status IN ('PENDING','RETRYING');

CREATE TABLE consumer_processed_events (
                                           event_id        UUID          NOT NULL,
                                           consumer_group  VARCHAR(100)  NOT NULL,
                                           processed_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                                           PRIMARY KEY (event_id, consumer_group)
);
CREATE INDEX idx_consumer_processed_at
    ON consumer_processed_events (processed_at);

DO $$
BEGIN
    PERFORM ensure_partitions('staged_income',     3, 2);
    PERFORM ensure_partitions('staged_expense',    3, 2);
    PERFORM ensure_partitions('staged_task',       3, 2);
    PERFORM ensure_partitions('staged_salary',     3, 2);
    PERFORM ensure_partitions('staged_crop_cycle', 3, 2);
END $$;