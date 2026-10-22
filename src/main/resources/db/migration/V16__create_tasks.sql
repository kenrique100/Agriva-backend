CREATE TABLE tasks (
                       id                     UUID          PRIMARY KEY,
                       farm_id                UUID          NOT NULL,
                       personnel_id           UUID          NOT NULL,
                       title                  VARCHAR(100)  NOT NULL,
                       description            VARCHAR(500),
                       deadline               DATE          NOT NULL,
                       status                 VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
                       expected_hours         INTEGER,
                       actual_hours           DOUBLE PRECISION,
                       total_worked_minutes   BIGINT        NOT NULL DEFAULT 0,
                       start_time             TIMESTAMP,
                       stop_time              TIMESTAMP,
                       last_resume_time       TIMESTAMP,
                       is_validated           BOOLEAN       NOT NULL DEFAULT FALSE,
                       validation_time        TIMESTAMP,
                       is_submitted           BOOLEAN       NOT NULL DEFAULT FALSE,
                       assigned_by_user_id    UUID,
                       validated_by_user_id   UUID,
                       created_at             TIMESTAMP     NOT NULL DEFAULT NOW(),
                       updated_at             TIMESTAMP     NOT NULL DEFAULT NOW(),

                       CONSTRAINT fk_tasks_farm
                           FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE CASCADE,
                       CONSTRAINT fk_tasks_personnel
                           FOREIGN KEY (personnel_id) REFERENCES personnel(id) ON DELETE CASCADE,
                       CONSTRAINT fk_tasks_assigned_by
                           FOREIGN KEY (assigned_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
                       CONSTRAINT fk_tasks_validated_by
                           FOREIGN KEY (validated_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
                       CONSTRAINT chk_tasks_status
                           CHECK (status IN ('PENDING','IN_PROGRESS','STOPPED','SUBMITTED','COMPLETED','UNCOMPLETED','CANCELLED')),
                       CONSTRAINT chk_tasks_minutes_non_negative
                           CHECK (total_worked_minutes >= 0)
);

CREATE INDEX idx_tasks_farm      ON tasks (farm_id);
CREATE INDEX idx_tasks_personnel ON tasks (personnel_id);
CREATE INDEX idx_tasks_status    ON tasks (status);
CREATE INDEX idx_tasks_deadline  ON tasks (deadline);
CREATE INDEX idx_tasks_submitted ON tasks (status, is_submitted, stop_time);