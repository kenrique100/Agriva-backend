
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
CREATE UNIQUE INDEX ux_phouse_farm_name_live ON poultry_house (farm_id, LOWER(name)) WHERE deleted_at IS NULL;
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
                               CONSTRAINT chk_pbatch_species CHECK (species IN ('BROILER','LAYER','COCKEREL','TURKEY','DUCK','OTHER')),
                               CONSTRAINT chk_pbatch_status  CHECK (status IN ('ACTIVE','CLOSED','CULLED')),
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

CREATE TABLE poultry_mortality_record (
                                          id                  UUID PRIMARY KEY,
                                          farm_id             UUID NOT NULL,
                                          batch_id            UUID NOT NULL,
                                          quantity            BIGINT NOT NULL,
                                          recorded_at         DATE NOT NULL,
                                          suspected_cause     VARCHAR(500),
                                          recorded_by_user_id UUID,
                                          created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
                                          CONSTRAINT fk_pmr_farm  FOREIGN KEY (farm_id)  REFERENCES farms (id) ON DELETE CASCADE,
                                          CONSTRAINT fk_pmr_batch FOREIGN KEY (batch_id) REFERENCES poultry_batch (id) ON DELETE CASCADE,
                                          CONSTRAINT fk_pmr_user  FOREIGN KEY (recorded_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                                          CONSTRAINT chk_pmr_quantity_positive CHECK (quantity > 0)
);
CREATE INDEX idx_pmr_farm       ON poultry_mortality_record (farm_id);
CREATE INDEX idx_pmr_batch      ON poultry_mortality_record (batch_id);
CREATE INDEX idx_pmr_batch_date ON poultry_mortality_record (batch_id, recorded_at DESC);

CREATE TABLE poultry_weight_sample (
                                       id                  UUID PRIMARY KEY,
                                       farm_id             UUID NOT NULL,
                                       batch_id            UUID NOT NULL,
                                       average_weight_g    NUMERIC(12,2) NOT NULL,
                                       sample_size         INT NOT NULL,
                                       sampled_at          DATE NOT NULL,
                                       recorded_by_user_id UUID,
                                       created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
                                       CONSTRAINT fk_pws_farm  FOREIGN KEY (farm_id)  REFERENCES farms (id) ON DELETE CASCADE,
                                       CONSTRAINT fk_pws_batch FOREIGN KEY (batch_id) REFERENCES poultry_batch (id) ON DELETE CASCADE,
                                       CONSTRAINT fk_pws_user  FOREIGN KEY (recorded_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                                       CONSTRAINT chk_pws_weight_positive CHECK (average_weight_g > 0),
                                       CONSTRAINT chk_pws_sample_positive CHECK (sample_size > 0)
);
CREATE INDEX idx_pws_farm       ON poultry_weight_sample (farm_id);
CREATE INDEX idx_pws_batch      ON poultry_weight_sample (batch_id);
CREATE INDEX idx_pws_batch_date ON poultry_weight_sample (batch_id, sampled_at DESC);

CREATE TABLE poultry_egg_record (
                                    id                  UUID PRIMARY KEY,
                                    farm_id             UUID NOT NULL,
                                    batch_id            UUID NOT NULL,
                                    eggs_collected      INT NOT NULL,
                                    broken_eggs         INT NOT NULL DEFAULT 0,
                                    recorded_at         DATE NOT NULL,
                                    recorded_by_user_id UUID,
                                    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
                                    CONSTRAINT fk_per_farm  FOREIGN KEY (farm_id)  REFERENCES farms (id) ON DELETE CASCADE,
                                    CONSTRAINT fk_per_batch FOREIGN KEY (batch_id) REFERENCES poultry_batch (id) ON DELETE CASCADE,
                                    CONSTRAINT fk_per_user  FOREIGN KEY (recorded_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                                    CONSTRAINT chk_per_eggs_non_negative   CHECK (eggs_collected >= 0),
                                    CONSTRAINT chk_per_broken_non_negative CHECK (broken_eggs >= 0),
                                    CONSTRAINT chk_per_broken_le_collected CHECK (broken_eggs <= eggs_collected)
);
CREATE INDEX idx_per_farm       ON poultry_egg_record (farm_id);
CREATE INDEX idx_per_batch      ON poultry_egg_record (batch_id);
CREATE INDEX idx_per_batch_date ON poultry_egg_record (batch_id, recorded_at DESC);