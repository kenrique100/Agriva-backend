

CREATE TABLE personnel (
                           id                               UUID PRIMARY KEY,
                           farm_id                          UUID          NOT NULL,
                           user_id                          UUID          NOT NULL,
                           name                             VARCHAR(100)  NOT NULL,
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
                           status                           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
                           status_change_timestamp          TIMESTAMP,
                           status_expiration                TIMESTAMP,
                           suspension_duration              INTERVAL,
                           termination_timestamp            TIMESTAMP,
                           working_days_count               INTEGER       NOT NULL DEFAULT 0,
                           last_productivity_reset_date     DATE,
                           current_period_start_date        DATE          NOT NULL DEFAULT CURRENT_DATE,
                           total_productive_days            INTEGER       NOT NULL DEFAULT 0,
                           last_productivity_update         DATE,
                           deleted_at                       TIMESTAMPTZ,
                           created_by                       VARCHAR(50),
                           updated_by                       VARCHAR(50),
                           created_at                       TIMESTAMP     NOT NULL DEFAULT NOW(),
                           updated_at                       TIMESTAMP     NOT NULL DEFAULT NOW(),
                           version                          BIGINT        NOT NULL DEFAULT 0,

                           CONSTRAINT fk_personnel_farm
                               FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                           CONSTRAINT fk_personnel_user
                               FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    -- 1:1 with users
                           CONSTRAINT ux_personnel_user_id UNIQUE (user_id),

    -- HR de-dup within a farm
                           CONSTRAINT ux_personnel_farm_national_id UNIQUE (farm_id, national_id),

                           CONSTRAINT chk_personnel_gender
                               CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','OTHER')),
                           CONSTRAINT chk_personnel_status
                               CHECK (status IN ('ACTIVE','INACTIVE','ON_LEAVE','SUSPENDED','TERMINATED'))
);

CREATE INDEX idx_personnel_farm   ON personnel (farm_id);
CREATE INDEX idx_personnel_user   ON personnel (user_id);
CREATE INDEX idx_personnel_status ON personnel (status);
CREATE INDEX idx_personnel_expiration
    ON personnel (status, status_expiration);

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
    ON personnel_status_history (personnel_id)
    WHERE end_timestamp IS NULL;