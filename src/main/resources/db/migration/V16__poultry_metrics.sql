-- Poultry Production Metrics
-- Adds three append-only record tables + a baseline weight column on
-- poultry_batch. Strictly additive.
--
-- Mortality records decrement poultry_batch.current_count under a
-- PESSIMISTIC_WRITE lock (in the service layer, not in SQL).
-- Weight samples update poultry_batch.average_weight_g and, on the very
-- first sample, seed initial_avg_weight_g.
-- Egg records are pure append (no batch mutation).

ALTER TABLE poultry_batch
    ADD COLUMN IF NOT EXISTS initial_avg_weight_g NUMERIC(12,2);

CREATE TABLE poultry_mortality_record (
                                          id                    UUID PRIMARY KEY,
                                          farm_id               UUID          NOT NULL,
                                          batch_id              UUID          NOT NULL,
                                          quantity              BIGINT        NOT NULL,
                                          recorded_at           DATE          NOT NULL,
                                          suspected_cause       VARCHAR(500),
                                          recorded_by_user_id   UUID,
                                          created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),

                                          CONSTRAINT fk_pmr_farm
                                              FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                          CONSTRAINT fk_pmr_batch
                                              FOREIGN KEY (batch_id) REFERENCES poultry_batch (id) ON DELETE CASCADE,
                                          CONSTRAINT fk_pmr_user
                                              FOREIGN KEY (recorded_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                                          CONSTRAINT chk_pmr_quantity_positive
                                              CHECK (quantity > 0)
);

CREATE INDEX idx_pmr_farm           ON poultry_mortality_record (farm_id);
CREATE INDEX idx_pmr_batch          ON poultry_mortality_record (batch_id);
CREATE INDEX idx_pmr_batch_date     ON poultry_mortality_record (batch_id, recorded_at DESC);

CREATE TABLE poultry_weight_sample (
                                       id                    UUID PRIMARY KEY,
                                       farm_id               UUID          NOT NULL,
                                       batch_id              UUID          NOT NULL,
                                       average_weight_g      NUMERIC(12,2) NOT NULL,
                                       sample_size           INT           NOT NULL,
                                       sampled_at            DATE          NOT NULL,
                                       recorded_by_user_id   UUID,
                                       created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),

                                       CONSTRAINT fk_pws_farm
                                           FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                       CONSTRAINT fk_pws_batch
                                           FOREIGN KEY (batch_id) REFERENCES poultry_batch (id) ON DELETE CASCADE,
                                       CONSTRAINT fk_pws_user
                                           FOREIGN KEY (recorded_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                                       CONSTRAINT chk_pws_weight_positive
                                           CHECK (average_weight_g > 0),
                                       CONSTRAINT chk_pws_sample_positive
                                           CHECK (sample_size > 0)
);

CREATE INDEX idx_pws_farm       ON poultry_weight_sample (farm_id);
CREATE INDEX idx_pws_batch      ON poultry_weight_sample (batch_id);
CREATE INDEX idx_pws_batch_date ON poultry_weight_sample (batch_id, sampled_at DESC);

CREATE TABLE poultry_egg_record (
                                    id                    UUID PRIMARY KEY,
                                    farm_id               UUID          NOT NULL,
                                    batch_id              UUID          NOT NULL,
                                    eggs_collected        INT           NOT NULL,
                                    broken_eggs           INT           NOT NULL DEFAULT 0,
                                    recorded_at           DATE          NOT NULL,
                                    recorded_by_user_id   UUID,
                                    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),

                                    CONSTRAINT fk_per_farm
                                        FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                    CONSTRAINT fk_per_batch
                                        FOREIGN KEY (batch_id) REFERENCES poultry_batch (id) ON DELETE CASCADE,
                                    CONSTRAINT fk_per_user
                                        FOREIGN KEY (recorded_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                                    CONSTRAINT chk_per_eggs_non_negative
                                        CHECK (eggs_collected >= 0),
                                    CONSTRAINT chk_per_broken_non_negative
                                        CHECK (broken_eggs >= 0),
                                    CONSTRAINT chk_per_broken_le_collected
                                        CHECK (broken_eggs <= eggs_collected)
);

CREATE INDEX idx_per_farm       ON poultry_egg_record (farm_id);
CREATE INDEX idx_per_batch      ON poultry_egg_record (batch_id);
CREATE INDEX idx_per_batch_date ON poultry_egg_record (batch_id, recorded_at DESC);