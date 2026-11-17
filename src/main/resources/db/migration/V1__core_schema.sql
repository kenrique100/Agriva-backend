

CREATE TABLE farms (
                       id         UUID PRIMARY KEY,
                       name       VARCHAR(100) NOT NULL,
                       master_id  UUID,
                       deleted_at TIMESTAMPTZ,
                       created_by VARCHAR(50),
                       updated_by VARCHAR(50),
                       created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                       updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                       version    BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE users (
                       id                 UUID PRIMARY KEY,
                       email              VARCHAR(100) NOT NULL,
                       username           VARCHAR(50),
                       password           VARCHAR(255) NOT NULL,
                       role               VARCHAR(20) NOT NULL,
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
        CHECK (role IN ('ADMIN','MASTER','SUB_USER','USER'));

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
                           id                              UUID PRIMARY KEY,
                           farm_id                         UUID NOT NULL,
                           user_id                         UUID NOT NULL,
                           name                            VARCHAR(100) NOT NULL,
                           gender                          VARCHAR(20),
                           date_of_birth                   DATE,
                           email                           VARCHAR(100),
                           national_id                     VARCHAR(15),
                           phone_number                    VARCHAR(20),
                           department                      VARCHAR(50),
                           date_of_employment              DATE,
                           profile_picture_path            VARCHAR(255),
                           profile_picture_thumbnail_path  VARCHAR(255),
                           total_hours_worked_last_30_days DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                           status                          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                           status_change_timestamp         TIMESTAMP,
                           status_expiration               TIMESTAMP,
                           suspension_duration             INTERVAL,
                           termination_timestamp           TIMESTAMP,
                           working_days_count              INTEGER NOT NULL DEFAULT 0,
                           last_productivity_reset_date    DATE,
                           current_period_start_date       DATE NOT NULL DEFAULT CURRENT_DATE,
                           total_productive_days           INTEGER NOT NULL DEFAULT 0,
                           last_productivity_update        DATE,
                           deleted_at                      TIMESTAMPTZ,
                           created_by                      VARCHAR(50),
                           updated_by                      VARCHAR(50),
                           created_at                      TIMESTAMP NOT NULL DEFAULT NOW(),
                           updated_at                      TIMESTAMP NOT NULL DEFAULT NOW(),
                           version                         BIGINT NOT NULL DEFAULT 0,

                           CONSTRAINT fk_personnel_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                           CONSTRAINT fk_personnel_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
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
                                          id                     UUID PRIMARY KEY,
                                          personnel_id           UUID NOT NULL,
                                          status                 VARCHAR(20) NOT NULL,
                                          start_timestamp        TIMESTAMP NOT NULL,
                                          end_timestamp          TIMESTAMP,
                                          allocated_duration     INTERVAL,
                                          actual_duration        INTERVAL,
                                          expected_end_timestamp TIMESTAMP,
                                          created_at             TIMESTAMP NOT NULL DEFAULT NOW(),

                                          CONSTRAINT fk_psh_personnel
                                              FOREIGN KEY (personnel_id) REFERENCES personnel (id) ON DELETE CASCADE,
                                          CONSTRAINT chk_psh_status
                                              CHECK (status IN ('ACTIVE','INACTIVE','ON_LEAVE','SUSPENDED','TERMINATED'))
);
CREATE INDEX idx_psh_personnel ON personnel_status_history (personnel_id);
CREATE INDEX idx_psh_open
    ON personnel_status_history (personnel_id) WHERE end_timestamp IS NULL;

CREATE TABLE tasks (
                       id                   UUID PRIMARY KEY,
                       farm_id              UUID NOT NULL,
                       personnel_id         UUID NOT NULL,
                       title                VARCHAR(100) NOT NULL,
                       description          VARCHAR(500),
                       deadline             DATE NOT NULL,
                       status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                       expected_hours       INTEGER,
                       actual_hours         DOUBLE PRECISION,
                       total_worked_minutes BIGINT NOT NULL DEFAULT 0,
                       start_time           TIMESTAMP,
                       stop_time            TIMESTAMP,
                       last_resume_time     TIMESTAMP,
                       is_validated         BOOLEAN NOT NULL DEFAULT FALSE,
                       validation_time      TIMESTAMP,
                       is_submitted         BOOLEAN NOT NULL DEFAULT FALSE,
                       assigned_by_user_id  UUID,
                       validated_by_user_id UUID,
                       deleted_at           TIMESTAMPTZ,
                       created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
                       updated_at           TIMESTAMP NOT NULL DEFAULT NOW(),
                       version              BIGINT NOT NULL DEFAULT 0,

                       CONSTRAINT fk_tasks_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                       CONSTRAINT fk_tasks_personnel FOREIGN KEY (personnel_id) REFERENCES personnel (id) ON DELETE CASCADE,
                       CONSTRAINT fk_tasks_assigned_by FOREIGN KEY (assigned_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                       CONSTRAINT fk_tasks_validated_by FOREIGN KEY (validated_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                       CONSTRAINT chk_tasks_status
                           CHECK (status IN ('PENDING','IN_PROGRESS','PAUSED','STOPPED','SUBMITTED',
                                             'COMPLETED','UNCOMPLETED','CANCELLED')),
                       CONSTRAINT chk_tasks_minutes_non_negative CHECK (total_worked_minutes >= 0)
);
CREATE INDEX idx_tasks_farm      ON tasks (farm_id);
CREATE INDEX idx_tasks_personnel ON tasks (personnel_id);
CREATE INDEX idx_tasks_status    ON tasks (status);
CREATE INDEX idx_tasks_deadline  ON tasks (deadline);
CREATE INDEX idx_tasks_submitted ON tasks (status, is_submitted, stop_time);
CREATE INDEX idx_tasks_deleted_at ON tasks (deleted_at);
CREATE INDEX idx_tasks_in_progress_resume
    ON tasks (status, last_resume_time)
    WHERE status = 'IN_PROGRESS' AND last_resume_time IS NOT NULL;

CREATE TABLE auth_events (
                             id         UUID PRIMARY KEY,
                             user_id    UUID,
                             event_type VARCHAR(50) NOT NULL,
                             ip_address VARCHAR(45),
                             user_agent VARCHAR(500),
                             metadata   JSONB,
                             created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_auth_events_user    ON auth_events (user_id, created_at DESC);
CREATE INDEX idx_auth_events_created ON auth_events (created_at DESC);

CREATE TABLE event_publication (
                                   id               UUID NOT NULL PRIMARY KEY,
                                   listener_id      TEXT NOT NULL,
                                   event_type       TEXT NOT NULL,
                                   serialized_event TEXT NOT NULL,
                                   publication_date TIMESTAMPTZ NOT NULL,
                                   completion_date  TIMESTAMPTZ
);
CREATE INDEX event_publication_serialized_event_hash_idx
    ON event_publication USING hash(serialized_event);
CREATE INDEX event_publication_completion_date_idx
    ON event_publication (completion_date);

CREATE TABLE transactions (
                              id                 UUID PRIMARY KEY,
                              farm_id            UUID NOT NULL,
                              transaction_ref    VARCHAR(50) NOT NULL,
                              occurred_at        TIMESTAMP NOT NULL,
                              status             VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                              notes              TEXT,
                              created_by_user_id UUID,
                              deleted_at         TIMESTAMPTZ,
                              created_by         VARCHAR(50),
                              updated_by         VARCHAR(50),
                              created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                              updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                              version            BIGINT NOT NULL DEFAULT 0,

                              CONSTRAINT fk_transactions_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                              CONSTRAINT fk_transactions_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                              CONSTRAINT chk_transactions_status CHECK (status IN ('DRAFT','POSTED','VOID'))
);
CREATE INDEX idx_transactions_farm     ON transactions (farm_id);
CREATE INDEX idx_transactions_occurred ON transactions (occurred_at);
CREATE INDEX idx_transactions_status   ON transactions (status);
CREATE UNIQUE INDEX ux_transactions_farm_ref
    ON transactions (farm_id, transaction_ref) WHERE deleted_at IS NULL;

CREATE TABLE investment (
                            id                 UUID PRIMARY KEY,
                            farm_id            UUID NOT NULL,
                            transaction_id     UUID,
                            amount             NUMERIC(19,4) NOT NULL,
                            currency           VARCHAR(3) NOT NULL DEFAULT 'USD',
                            investor_name      VARCHAR(100) NOT NULL,
                            type               VARCHAR(50),
                            description        VARCHAR(500),
                            investment_date    DATE NOT NULL,
                            created_by_user_id UUID,
                            active             BOOLEAN NOT NULL DEFAULT FALSE,
                            activated_at       TIMESTAMP,
                            deleted_at         TIMESTAMPTZ,
                            created_by         VARCHAR(50),
                            updated_by         VARCHAR(50),
                            created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                            updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                            version            BIGINT NOT NULL DEFAULT 0,

                            CONSTRAINT fk_investment_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                            CONSTRAINT fk_investment_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                            CONSTRAINT fk_investment_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE SET NULL,
                            CONSTRAINT chk_investment_currency CHECK (char_length(currency) = 3),
                            CONSTRAINT chk_investment_amount_positive CHECK (amount > 0)
);
CREATE INDEX idx_investment_farm_id         ON investment (farm_id);
CREATE INDEX idx_investment_investment_date ON investment (investment_date);
CREATE INDEX idx_investment_farm_date       ON investment (farm_id, investment_date);
CREATE INDEX idx_investment_creator         ON investment (created_by_user_id);
CREATE INDEX idx_investment_transaction     ON investment (transaction_id);
CREATE UNIQUE INDEX uq_investment_one_active_per_farm
    ON investment (farm_id) WHERE active = TRUE;
CREATE INDEX idx_investment_active
    ON investment (farm_id) WHERE active = TRUE AND deleted_at IS NULL;

CREATE TABLE expense (
                         id                 UUID PRIMARY KEY,
                         farm_id            UUID NOT NULL,
                         transaction_id     UUID,
                         investment_id      UUID,
                         reason             VARCHAR(255) NOT NULL,
                         expense_date       DATE NOT NULL,
                         qty_purchased      INT NOT NULL DEFAULT 1,
                         amount_paid        NUMERIC(19,4) NOT NULL,
                         expected_amount    NUMERIC(19,4) NOT NULL DEFAULT 0,
                         due_balance        NUMERIC(19,4) NOT NULL DEFAULT 0,
                         receipt            VARCHAR(255),
                         created_by_user_id UUID,
                         currency           VARCHAR(3) NOT NULL DEFAULT 'XAF',
                         category           VARCHAR(50) NOT NULL,
                         deleted_at         TIMESTAMPTZ,
                         created_by         VARCHAR(50),
                         updated_by         VARCHAR(50),
                         created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                         updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                         version            BIGINT NOT NULL DEFAULT 0,

                         CONSTRAINT fk_expense_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                         CONSTRAINT fk_expense_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                         CONSTRAINT fk_expense_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE SET NULL,
                         CONSTRAINT fk_expense_investment FOREIGN KEY (investment_id) REFERENCES investment (id) ON DELETE SET NULL,
                         CONSTRAINT chk_expense_currency CHECK (char_length(currency) = 3),
                         CONSTRAINT chk_expense_amount_positive CHECK (amount_paid > 0)
);
CREATE INDEX idx_expense_farm_id       ON expense (farm_id);
CREATE INDEX idx_expense_expense_date  ON expense (expense_date);
CREATE INDEX idx_expense_farm_date     ON expense (farm_id, expense_date);
CREATE INDEX idx_expense_creator       ON expense (created_by_user_id);
CREATE INDEX idx_expense_transaction   ON expense (transaction_id);
CREATE INDEX idx_expense_investment_id ON expense (investment_id);

CREATE TABLE income (
                        id                 UUID PRIMARY KEY,
                        farm_id            UUID NOT NULL,
                        transaction_id     UUID,
                        reason             VARCHAR(255) NOT NULL,
                        income_date        DATE NOT NULL,
                        quantity           INT NOT NULL DEFAULT 1,
                        amount_received    NUMERIC(19,4) NOT NULL,
                        expected_amount    NUMERIC(19,4) NOT NULL DEFAULT 0,
                        due_balance        NUMERIC(19,4) NOT NULL DEFAULT 0,
                        receipt            VARCHAR(255),
                        created_by_user_id UUID,
                        currency           VARCHAR(3) NOT NULL DEFAULT 'XAF',
                        category           VARCHAR(50) NOT NULL,
                        deleted_at         TIMESTAMPTZ,
                        created_by         VARCHAR(50),
                        updated_by         VARCHAR(50),
                        created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                        updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                        version            BIGINT NOT NULL DEFAULT 0,

                        CONSTRAINT fk_income_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                        CONSTRAINT fk_income_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                        CONSTRAINT fk_income_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE SET NULL,
                        CONSTRAINT chk_income_currency CHECK (char_length(currency) = 3),
                        CONSTRAINT chk_income_amount_positive CHECK (amount_received > 0)
);
CREATE INDEX idx_income_farm_id     ON income (farm_id);
CREATE INDEX idx_income_income_date ON income (income_date);
CREATE INDEX idx_income_farm_date   ON income (farm_id, income_date);
CREATE INDEX idx_income_creator     ON income (created_by_user_id);
CREATE INDEX idx_income_transaction ON income (transaction_id);

CREATE TABLE salary_payments (
                                 id                UUID PRIMARY KEY,
                                 farm_id           UUID NOT NULL,
                                 personnel_id      UUID NOT NULL,
                                 amount            NUMERIC(19,4) NOT NULL,
                                 payment_date      DATE NOT NULL,
                                 status            VARCHAR(20) NOT NULL DEFAULT 'PROCESSED',
                                 payment_reference VARCHAR(50) NOT NULL,
                                 expense_id        UUID,
                                 paid_by_user_id   UUID,
                                 created_at        TIMESTAMP NOT NULL DEFAULT NOW(),

                                 CONSTRAINT fk_sp_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                 CONSTRAINT fk_sp_personnel FOREIGN KEY (personnel_id) REFERENCES personnel (id) ON DELETE CASCADE,
                                 CONSTRAINT fk_sp_expense FOREIGN KEY (expense_id) REFERENCES expense (id) ON DELETE SET NULL,
                                 CONSTRAINT fk_sp_paid_by FOREIGN KEY (paid_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                                 CONSTRAINT ux_salary_payments_ref UNIQUE (payment_reference),
                                 CONSTRAINT chk_sp_status CHECK (status IN ('PENDING','PROCESSED','FAILED','CANCELLED')),
                                 CONSTRAINT chk_sp_amount_positive CHECK (amount > 0)
);
CREATE INDEX idx_sp_farm      ON salary_payments (farm_id);
CREATE INDEX idx_sp_personnel ON salary_payments (personnel_id);
CREATE INDEX idx_sp_date      ON salary_payments (payment_date);
CREATE INDEX idx_sp_expense   ON salary_payments (expense_id);