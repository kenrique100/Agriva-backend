CREATE TABLE farms (
                       id          UUID PRIMARY KEY,
                       name        VARCHAR(100) NOT NULL,
                       master_id   UUID,
                       deleted_at  TIMESTAMPTZ,
                       created_by  VARCHAR(50),
                       updated_by  VARCHAR(50),
                       created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
                       updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
                       version     BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE users (
                       id                 UUID PRIMARY KEY,
                       email              VARCHAR(100) NOT NULL,
                       username           VARCHAR(50),
                       password           VARCHAR(255) NOT NULL,
                       role               VARCHAR(20)  NOT NULL,
                       farm_id            UUID,
                       gender             VARCHAR(20),
                       date_of_birth      DATE,
                       phone_number       VARCHAR(20),
                       address            VARCHAR(255),
                       city               VARCHAR(100),
                       state              VARCHAR(100),
                       country            VARCHAR(100),
                       postal_code        VARCHAR(20),
                       preferred_currency VARCHAR(3) DEFAULT 'XAF',
                       deleted_at         TIMESTAMPTZ,
                       created_by         VARCHAR(50),
                       updated_by         VARCHAR(50),
                       created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                       updated_at         TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE farms
    ADD CONSTRAINT fk_farms_master
        FOREIGN KEY (master_id) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE users
    ADD CONSTRAINT fk_users_farm
        FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE SET NULL;

ALTER TABLE users
    ADD CONSTRAINT chk_users_role
        CHECK (role IN ('ADMIN', 'MASTER', 'SUB_USER', 'USER'));

CREATE UNIQUE INDEX ux_users_email_live
    ON users (LOWER(email)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_users_username_live
    ON users (LOWER(username)) WHERE deleted_at IS NULL AND username IS NOT NULL;
CREATE UNIQUE INDEX ux_farms_name_live
    ON farms (LOWER(name)) WHERE deleted_at IS NULL;

CREATE INDEX idx_users_farm_id   ON users (farm_id);
CREATE INDEX idx_users_role      ON users (role);
CREATE INDEX idx_farms_master_id ON farms (master_id);

CREATE TABLE personnel (
                           id                               UUID PRIMARY KEY,
                           farm_id                          UUID         NOT NULL,
                           user_id                          UUID         NOT NULL,
                           name                             VARCHAR(100) NOT NULL,
                           gender                           VARCHAR(20),
                           date_of_birth                    DATE,
                           email                            VARCHAR(100),
                           national_id                      VARCHAR(15),
                           phone_number                     VARCHAR(20),
                           department                       VARCHAR(50),
                           date_of_employment               DATE,
                           profile_picture_path             VARCHAR(255),
                           profile_picture_thumbnail_path   VARCHAR(255),
                           total_hours_worked_last_30_days  DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                           status                           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                           status_change_timestamp          TIMESTAMP,
                           status_expiration                TIMESTAMP,
                           suspension_duration              INTERVAL,
                           termination_timestamp            TIMESTAMP,
                           working_days_count               INTEGER      NOT NULL DEFAULT 0,
                           last_productivity_reset_date     DATE,
                           current_period_start_date        DATE         NOT NULL DEFAULT CURRENT_DATE,
                           total_productive_days            INTEGER      NOT NULL DEFAULT 0,
                           last_productivity_update         DATE,
                           deleted_at                       TIMESTAMPTZ,
                           created_by                       VARCHAR(50),
                           updated_by                       VARCHAR(50),
                           created_at                       TIMESTAMP    NOT NULL DEFAULT NOW(),
                           updated_at                       TIMESTAMP    NOT NULL DEFAULT NOW(),
                           version                          BIGINT       NOT NULL DEFAULT 0,

                           CONSTRAINT fk_personnel_farm
                               FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                           CONSTRAINT fk_personnel_user
                               FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
                           CONSTRAINT ux_personnel_user_id UNIQUE (user_id),
                           CONSTRAINT ux_personnel_farm_national_id UNIQUE (farm_id, national_id),
                           CONSTRAINT chk_personnel_gender
                               CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','OTHER')),
                           CONSTRAINT chk_personnel_status
                               CHECK (status IN ('ACTIVE','INACTIVE','ON_LEAVE','SUSPENDED','TERMINATED'))
);

CREATE INDEX idx_personnel_farm       ON personnel (farm_id);
CREATE INDEX idx_personnel_user       ON personnel (user_id);
CREATE INDEX idx_personnel_status     ON personnel (status);
CREATE INDEX idx_personnel_expiration ON personnel (status, status_expiration);

CREATE TABLE personnel_status_history (
                                          id                      UUID PRIMARY KEY,
                                          personnel_id            UUID         NOT NULL,
                                          status                  VARCHAR(20)  NOT NULL,
                                          start_timestamp         TIMESTAMP    NOT NULL,
                                          end_timestamp           TIMESTAMP,
                                          allocated_duration      INTERVAL,
                                          actual_duration         INTERVAL,
                                          expected_end_timestamp  TIMESTAMP,
                                          created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),

                                          CONSTRAINT fk_psh_personnel
                                              FOREIGN KEY (personnel_id) REFERENCES personnel (id) ON DELETE CASCADE,
                                          CONSTRAINT chk_psh_status
                                              CHECK (status IN ('ACTIVE','INACTIVE','ON_LEAVE','SUSPENDED','TERMINATED'))
);

CREATE INDEX idx_psh_personnel ON personnel_status_history (personnel_id);
CREATE INDEX idx_psh_open
    ON personnel_status_history (personnel_id) WHERE end_timestamp IS NULL;

CREATE TABLE tasks (
                       id                    UUID PRIMARY KEY,
                       farm_id               UUID         NOT NULL,
                       personnel_id          UUID         NOT NULL,
                       title                 VARCHAR(100) NOT NULL,
                       description           VARCHAR(500),
                       deadline              DATE         NOT NULL,
                       status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                       expected_hours        INTEGER,
                       actual_hours          DOUBLE PRECISION,
                       total_worked_minutes  BIGINT       NOT NULL DEFAULT 0,
                       start_time            TIMESTAMP,
                       stop_time             TIMESTAMP,
                       last_resume_time      TIMESTAMP,
                       is_validated          BOOLEAN      NOT NULL DEFAULT FALSE,
                       validation_time       TIMESTAMP,
                       is_submitted          BOOLEAN      NOT NULL DEFAULT FALSE,
                       assigned_by_user_id   UUID,
                       validated_by_user_id  UUID,
                       deleted_at            TIMESTAMPTZ,
                       created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
                       updated_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
                       version               BIGINT       NOT NULL DEFAULT 0,

                       CONSTRAINT fk_tasks_farm
                           FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                       CONSTRAINT fk_tasks_personnel
                           FOREIGN KEY (personnel_id) REFERENCES personnel (id) ON DELETE CASCADE,
                       CONSTRAINT fk_tasks_assigned_by
                           FOREIGN KEY (assigned_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                       CONSTRAINT fk_tasks_validated_by
                           FOREIGN KEY (validated_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                       CONSTRAINT chk_tasks_status
                           CHECK (status IN (
                                             'PENDING','IN_PROGRESS','PAUSED','STOPPED','SUBMITTED',
                                             'COMPLETED','UNCOMPLETED','CANCELLED'
                               )),
                       CONSTRAINT chk_tasks_minutes_non_negative
                           CHECK (total_worked_minutes >= 0)
);

CREATE INDEX idx_tasks_farm         ON tasks (farm_id);
CREATE INDEX idx_tasks_personnel    ON tasks (personnel_id);
CREATE INDEX idx_tasks_status       ON tasks (status);
CREATE INDEX idx_tasks_deadline     ON tasks (deadline);
CREATE INDEX idx_tasks_submitted    ON tasks (status, is_submitted, stop_time);
CREATE INDEX idx_tasks_deleted_at   ON tasks (deleted_at);
CREATE INDEX idx_tasks_in_progress_resume
    ON tasks (status, last_resume_time)
    WHERE status = 'IN_PROGRESS' AND last_resume_time IS NOT NULL;

CREATE TABLE auth_events (
                             id           UUID PRIMARY KEY,
                             user_id      UUID,
                             event_type   VARCHAR(50) NOT NULL,
                             ip_address   VARCHAR(45),
                             user_agent   VARCHAR(500),
                             metadata     JSONB,
                             created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_auth_events_user    ON auth_events (user_id, created_at DESC);
CREATE INDEX idx_auth_events_created ON auth_events (created_at DESC);

CREATE TABLE IF NOT EXISTS event_publication (
                                                 id              UUID NOT NULL,
                                                 listener_id     TEXT NOT NULL,
                                                 event_type      TEXT NOT NULL,
                                                 serialized_event TEXT NOT NULL,
                                                 publication_date TIMESTAMPTZ NOT NULL,
                                                 completion_date  TIMESTAMPTZ,
                                                 PRIMARY KEY (id)
    );
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx
    ON event_publication USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_completion_date_idx
    ON event_publication (completion_date);