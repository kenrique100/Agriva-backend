
-- 1. Add PAUSED to the tasks status CHECK constraint
ALTER TABLE tasks DROP CONSTRAINT IF EXISTS chk_tasks_status;
ALTER TABLE tasks ADD CONSTRAINT chk_tasks_status
    CHECK (status IN (
                      'PENDING','IN_PROGRESS','PAUSED','STOPPED','SUBMITTED',
                      'COMPLETED','UNCOMPLETED','CANCELLED'
        ));

-- 2. staged_task
CREATE TABLE staged_task (
                             id                   UUID          PRIMARY KEY,
                             farm_id              UUID          NOT NULL,
                             payload_json         TEXT          NOT NULL,
                             status               VARCHAR(20)   NOT NULL,
                             validation_errors    VARCHAR(2000),
                             submitted_by_user_id UUID,
                             submitted_by_email   VARCHAR(100),
                             submitted_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
                             last_attempt_at      TIMESTAMP,
                             next_retry_at        TIMESTAMP,
                             processed_at         TIMESTAMP,
                             moved_task_id        UUID,
                             retry_count          INT           NOT NULL DEFAULT 0,

                             CONSTRAINT fk_staged_task_farm
                                 FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE CASCADE,
                             CONSTRAINT fk_staged_task_submitter
                                 FOREIGN KEY (submitted_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
                             CONSTRAINT fk_staged_task_moved
                                 FOREIGN KEY (moved_task_id) REFERENCES tasks(id) ON DELETE SET NULL,
                             CONSTRAINT chk_staged_task_status
                                 CHECK (status IN ('PENDING','RETRYING','MOVED','FAILED')),
                             CONSTRAINT chk_staged_task_retry_non_negative
                                 CHECK (retry_count >= 0)
);

CREATE INDEX idx_staged_task_status       ON staged_task(status);
CREATE INDEX idx_staged_task_farm_id      ON staged_task(farm_id);
CREATE INDEX idx_staged_task_status_farm  ON staged_task(status, farm_id);
CREATE INDEX idx_staged_task_due          ON staged_task(status, next_retry_at);

-- 3. staged_salary
CREATE TABLE staged_salary (
                               id                   UUID          PRIMARY KEY,
                               farm_id              UUID          NOT NULL,
                               payload_json         TEXT          NOT NULL,
                               status               VARCHAR(20)   NOT NULL,
                               validation_errors    VARCHAR(2000),
                               submitted_by_user_id UUID,
                               submitted_by_email   VARCHAR(100),
                               submitted_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
                               last_attempt_at      TIMESTAMP,
                               next_retry_at        TIMESTAMP,
                               processed_at         TIMESTAMP,
                               moved_salary_id      UUID,
                               retry_count          INT           NOT NULL DEFAULT 0,

                               CONSTRAINT fk_staged_salary_farm
                                   FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE CASCADE,
                               CONSTRAINT fk_staged_salary_submitter
                                   FOREIGN KEY (submitted_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
                               CONSTRAINT fk_staged_salary_moved
                                   FOREIGN KEY (moved_salary_id) REFERENCES salary_payments(id) ON DELETE SET NULL,
                               CONSTRAINT chk_staged_salary_status
                                   CHECK (status IN ('PENDING','RETRYING','MOVED','FAILED')),
                               CONSTRAINT chk_staged_salary_retry_non_negative
                                   CHECK (retry_count >= 0)
);

CREATE INDEX idx_staged_salary_status       ON staged_salary(status);
CREATE INDEX idx_staged_salary_farm_id      ON staged_salary(farm_id);
CREATE INDEX idx_staged_salary_status_farm  ON staged_salary(status, farm_id);
CREATE INDEX idx_staged_salary_due          ON staged_salary(status, next_retry_at);