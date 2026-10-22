CREATE TABLE farms (
                       id          UUID         PRIMARY KEY,
                       name        VARCHAR(100) NOT NULL,
                       master_id   UUID,
                       created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
                       updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
                       CONSTRAINT ux_farms_name UNIQUE (name)
);

CREATE TABLE users (
                       id          UUID         PRIMARY KEY,
                       email       VARCHAR(100) NOT NULL,
                       username    VARCHAR(50),
                       password    VARCHAR(255) NOT NULL,
                       role        VARCHAR(20)  NOT NULL,
                       farm_id     UUID,
                       created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
                       updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
                       CONSTRAINT ux_users_email    UNIQUE (email),
                       CONSTRAINT ux_users_username UNIQUE (username)
);

ALTER TABLE farms
    ADD CONSTRAINT fk_farms_master
        FOREIGN KEY (master_id) REFERENCES users (id)
            ON DELETE SET NULL;

ALTER TABLE users
    ADD CONSTRAINT fk_users_farm
        FOREIGN KEY (farm_id) REFERENCES farms (id)
            ON DELETE SET NULL;