
CREATE TABLE farms (
                       id          UUID PRIMARY KEY,
                       name        VARCHAR(100) NOT NULL,
                       master_id   UUID,
                       deleted_at  TIMESTAMPTZ,
                       created_by  VARCHAR(50),
                       updated_by  VARCHAR(50),
                       created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
                       updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
                       version     BIGINT    NOT NULL DEFAULT 0
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

CREATE INDEX idx_users_farm_id    ON users (farm_id);
CREATE INDEX idx_users_role       ON users (role);
CREATE INDEX idx_farms_master_id  ON farms (master_id);

-- Partial unique indexes so soft-deleted rows do not block re-creation
-- of the same email / username / farm name.
CREATE UNIQUE INDEX ux_users_email_live
    ON users (LOWER(email))
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_users_username_live
    ON users (LOWER(username))
    WHERE deleted_at IS NULL AND username IS NOT NULL;

CREATE UNIQUE INDEX ux_farms_name_live
    ON farms (LOWER(name))
    WHERE deleted_at IS NULL;