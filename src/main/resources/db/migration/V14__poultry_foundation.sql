

CREATE TABLE poultry_house (
                               id                 UUID PRIMARY KEY,
                               farm_id            UUID NOT NULL,
                               name               VARCHAR(100) NOT NULL,
                               location           VARCHAR(255),
                               capacity           INTEGER,
                               active             BOOLEAN NOT NULL DEFAULT TRUE,
                               created_by_user_id UUID,
                               deleted_at         TIMESTAMPTZ,
                               created_by         VARCHAR(50),
                               updated_by         VARCHAR(50),
                               created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                               updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                               version            BIGINT NOT NULL DEFAULT 0,

                               CONSTRAINT fk_phouse_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                               CONSTRAINT fk_phouse_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                               CONSTRAINT chk_phouse_name_non_empty CHECK (LENGTH(TRIM(name)) > 0),
                               CONSTRAINT chk_phouse_capacity_positive CHECK (capacity IS NULL OR capacity > 0)
);
CREATE UNIQUE INDEX ux_phouse_farm_name_live
    ON poultry_house (farm_id, LOWER(name)) WHERE deleted_at IS NULL;
CREATE INDEX idx_phouse_farm_id     ON poultry_house (farm_id);
CREATE INDEX idx_phouse_farm_active ON poultry_house (farm_id, active) WHERE deleted_at IS NULL;
CREATE INDEX idx_phouse_live        ON poultry_house (farm_id) WHERE deleted_at IS NULL;

CREATE TABLE poultry_batch (
                               id                   UUID PRIMARY KEY,
                               farm_id              UUID NOT NULL,
                               house_id             UUID NOT NULL,
                               species              VARCHAR(20) NOT NULL,
                               batch_code           VARCHAR(30) NOT NULL,
                               initial_count        BIGINT NOT NULL,
                               current_count        BIGINT NOT NULL,
                               arrival_date         DATE NOT NULL,
                               stage                VARCHAR(30),
                               average_weight_g     NUMERIC(12,2),
                               initial_avg_weight_g NUMERIC(12,2),
                               status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                               deleted_at           TIMESTAMPTZ,
                               created_by           VARCHAR(50),
                               updated_by           VARCHAR(50),
                               created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
                               updated_at           TIMESTAMP NOT NULL DEFAULT NOW(),
                               version              BIGINT NOT NULL DEFAULT 0,

                               CONSTRAINT fk_pbatch_farm  FOREIGN KEY (farm_id)  REFERENCES farms (id) ON DELETE CASCADE,
                               CONSTRAINT fk_pbatch_house FOREIGN KEY (house_id) REFERENCES poultry_house (id) ON DELETE CASCADE,
                               CONSTRAINT chk_pbatch_species
                                   CHECK (species IN ('BROILER','LAYER','COCKEREL','TURKEY','DUCK','OTHER')),
                               CONSTRAINT chk_pbatch_status
                                   CHECK (status IN ('ACTIVE','CLOSED','CULLED')),
                               CONSTRAINT chk_pbatch_initial_positive            CHECK (initial_count > 0),
                               CONSTRAINT chk_pbatch_current_non_negative        CHECK (current_count >= 0),
                               CONSTRAINT chk_pbatch_weight_non_negative         CHECK (average_weight_g IS NULL OR average_weight_g >= 0),
                               CONSTRAINT chk_pbatch_initial_weight_non_negative CHECK (initial_avg_weight_g IS NULL OR initial_avg_weight_g >= 0),
                               CONSTRAINT ux_pbatch_farm_code                    UNIQUE (farm_id, batch_code)
);
CREATE INDEX idx_pbatch_farm         ON poultry_batch (farm_id);
CREATE INDEX idx_pbatch_house        ON poultry_batch (house_id);
CREATE INDEX idx_pbatch_farm_status  ON poultry_batch (farm_id, status) WHERE status = 'ACTIVE';
CREATE INDEX idx_pbatch_house_active ON poultry_batch (house_id, status) WHERE status = 'ACTIVE';
CREATE INDEX idx_pbatch_arrival_desc ON poultry_batch (farm_id, arrival_date DESC);
CREATE INDEX idx_pbatch_code         ON poultry_batch (batch_code);