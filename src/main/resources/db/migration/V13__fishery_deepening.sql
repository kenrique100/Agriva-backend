

CREATE TABLE treatment (
                           id                      UUID PRIMARY KEY,
                           farm_id                 UUID NOT NULL,
                           pond_id                 UUID NOT NULL,
                           medication_name         VARCHAR(100) NOT NULL,
                           dosage                  NUMERIC(19,4) NOT NULL,
                           dosage_unit             VARCHAR(20) NOT NULL,
                           administered_at         DATE NOT NULL,
                           withdrawal_days         INT,
                           administered_by_user_id UUID,
                           notes                   VARCHAR(500),
                           created_at              TIMESTAMP NOT NULL DEFAULT NOW(),

                           CONSTRAINT fk_treatment_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                           CONSTRAINT fk_treatment_pond FOREIGN KEY (pond_id) REFERENCES ponds (id) ON DELETE CASCADE,
                           CONSTRAINT fk_treatment_user FOREIGN KEY (administered_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                           CONSTRAINT chk_treatment_dosage_positive         CHECK (dosage > 0),
                           CONSTRAINT chk_treatment_withdrawal_non_negative CHECK (withdrawal_days IS NULL OR withdrawal_days >= 0)
);
CREATE INDEX idx_treatment_farm           ON treatment (farm_id);
CREATE INDEX idx_treatment_pond           ON treatment (pond_id);
CREATE INDEX idx_treatment_pond_date_desc ON treatment (pond_id, administered_at DESC);

CREATE TABLE mortality_record (
                                  id                   UUID PRIMARY KEY,
                                  farm_id              UUID NOT NULL,
                                  pond_id              UUID NOT NULL,
                                  species              VARCHAR(50) NOT NULL,
                                  quantity             BIGINT NOT NULL,
                                  recorded_at          DATE NOT NULL,
                                  suspected_cause      VARCHAR(500),
                                  recorded_by_user_id  UUID,
                                  created_at           TIMESTAMP NOT NULL DEFAULT NOW(),

                                  CONSTRAINT fk_mortality_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                  CONSTRAINT fk_mortality_pond FOREIGN KEY (pond_id) REFERENCES ponds (id) ON DELETE CASCADE,
                                  CONSTRAINT fk_mortality_user FOREIGN KEY (recorded_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                                  CONSTRAINT chk_mortality_qty_positive CHECK (quantity > 0)
);
CREATE INDEX idx_mortality_farm           ON mortality_record (farm_id);
CREATE INDEX idx_mortality_pond           ON mortality_record (pond_id);
CREATE INDEX idx_mortality_pond_date_desc ON mortality_record (pond_id, recorded_at DESC);

CREATE TABLE growth_sample (
                               id                   UUID PRIMARY KEY,
                               farm_id              UUID NOT NULL,
                               pond_id              UUID NOT NULL,
                               species              VARCHAR(50) NOT NULL,
                               average_weight_g     NUMERIC(12,2) NOT NULL,
                               sample_size          INT NOT NULL,
                               sampled_at           DATE NOT NULL,
                               recorded_by_user_id  UUID,
                               created_at           TIMESTAMP NOT NULL DEFAULT NOW(),

                               CONSTRAINT fk_growth_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                               CONSTRAINT fk_growth_pond FOREIGN KEY (pond_id) REFERENCES ponds (id) ON DELETE CASCADE,
                               CONSTRAINT fk_growth_user FOREIGN KEY (recorded_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                               CONSTRAINT chk_growth_weight_positive      CHECK (average_weight_g > 0),
                               CONSTRAINT chk_growth_sample_size_positive CHECK (sample_size > 0)
);
CREATE INDEX idx_growth_farm   ON growth_sample (farm_id);
CREATE INDEX idx_growth_pond   ON growth_sample (pond_id);
CREATE INDEX idx_growth_pond_species_date_desc
    ON growth_sample (pond_id, species, sampled_at DESC);