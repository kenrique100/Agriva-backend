

CREATE TABLE staged_feeding_ingredient (
                                           id                   UUID NOT NULL,
                                           farm_id              UUID NOT NULL,
                                           payload_json         TEXT NOT NULL,
                                           status               VARCHAR(20) NOT NULL,
                                           validation_errors    VARCHAR(2000),
                                           submitted_by_user_id UUID,
                                           submitted_by_email   VARCHAR(100),
                                           submitted_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                                           last_attempt_at      TIMESTAMP,
                                           next_retry_at        TIMESTAMP,
                                           processed_at         TIMESTAMP,
                                           moved_ingredient_id  UUID,
                                           retry_count          INT NOT NULL DEFAULT 0,
                                           version              BIGINT NOT NULL DEFAULT 0,
                                           PRIMARY KEY (id, submitted_at),
                                           CONSTRAINT fk_staged_feeding_ingredient_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                           CONSTRAINT fk_staged_feeding_ingredient_submitter FOREIGN KEY (submitted_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                                           CONSTRAINT fk_staged_feeding_ingredient_moved FOREIGN KEY (moved_ingredient_id) REFERENCES ingredient (id) ON DELETE SET NULL,
                                           CONSTRAINT chk_staged_feeding_ingredient_status CHECK (status IN ('PENDING','RETRYING','MOVED','FAILED')),
                                           CONSTRAINT chk_staged_feeding_ingredient_retry_non_negative CHECK (retry_count >= 0)
) PARTITION BY RANGE (submitted_at);
CREATE INDEX idx_staged_feeding_ingredient_status   ON staged_feeding_ingredient (status);
CREATE INDEX idx_staged_feeding_ingredient_farm_id  ON staged_feeding_ingredient (farm_id);
CREATE INDEX idx_staged_feeding_ingredient_due      ON staged_feeding_ingredient (status, next_retry_at);
CREATE INDEX idx_staged_feeding_ingredient_live_due ON staged_feeding_ingredient (next_retry_at)
    WHERE status IN ('PENDING','RETRYING');

CREATE TABLE staged_feeding_formulation (
                                            id                   UUID NOT NULL,
                                            farm_id              UUID NOT NULL,
                                            payload_json         TEXT NOT NULL,
                                            status               VARCHAR(20) NOT NULL,
                                            validation_errors    VARCHAR(2000),
                                            submitted_by_user_id UUID,
                                            submitted_by_email   VARCHAR(100),
                                            submitted_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                                            last_attempt_at      TIMESTAMP,
                                            next_retry_at        TIMESTAMP,
                                            processed_at         TIMESTAMP,
                                            moved_formulation_id UUID,
                                            retry_count          INT NOT NULL DEFAULT 0,
                                            version              BIGINT NOT NULL DEFAULT 0,
                                            PRIMARY KEY (id, submitted_at),
                                            CONSTRAINT fk_staged_feeding_formulation_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                            CONSTRAINT fk_staged_feeding_formulation_submitter FOREIGN KEY (submitted_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                                            CONSTRAINT fk_staged_feeding_formulation_moved FOREIGN KEY (moved_formulation_id) REFERENCES formulation (id) ON DELETE SET NULL,
                                            CONSTRAINT chk_staged_feeding_formulation_status CHECK (status IN ('PENDING','RETRYING','MOVED','FAILED')),
                                            CONSTRAINT chk_staged_feeding_formulation_retry_non_negative CHECK (retry_count >= 0)
) PARTITION BY RANGE (submitted_at);
CREATE INDEX idx_staged_feeding_formulation_status   ON staged_feeding_formulation (status);
CREATE INDEX idx_staged_feeding_formulation_farm_id  ON staged_feeding_formulation (farm_id);
CREATE INDEX idx_staged_feeding_formulation_due      ON staged_feeding_formulation (status, next_retry_at);
CREATE INDEX idx_staged_feeding_formulation_live_due ON staged_feeding_formulation (next_retry_at)
    WHERE status IN ('PENDING','RETRYING');

CREATE TABLE staged_feeding_production (
                                           id                   UUID NOT NULL,
                                           farm_id              UUID NOT NULL,
                                           payload_json         TEXT NOT NULL,
                                           status               VARCHAR(20) NOT NULL,
                                           validation_errors    VARCHAR(2000),
                                           submitted_by_user_id UUID,
                                           submitted_by_email   VARCHAR(100),
                                           submitted_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                                           last_attempt_at      TIMESTAMP,
                                           next_retry_at        TIMESTAMP,
                                           processed_at         TIMESTAMP,
                                           moved_batch_id       UUID,
                                           retry_count          INT NOT NULL DEFAULT 0,
                                           version              BIGINT NOT NULL DEFAULT 0,
                                           PRIMARY KEY (id, submitted_at),
                                           CONSTRAINT fk_staged_feeding_production_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                           CONSTRAINT fk_staged_feeding_production_submitter FOREIGN KEY (submitted_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                                           CONSTRAINT fk_staged_feeding_production_moved FOREIGN KEY (moved_batch_id) REFERENCES feed_batch (id) ON DELETE SET NULL,
                                           CONSTRAINT chk_staged_feeding_production_status CHECK (status IN ('PENDING','RETRYING','MOVED','FAILED')),
                                           CONSTRAINT chk_staged_feeding_production_retry_non_negative CHECK (retry_count >= 0)
) PARTITION BY RANGE (submitted_at);
CREATE INDEX idx_staged_feeding_production_status   ON staged_feeding_production (status);
CREATE INDEX idx_staged_feeding_production_farm_id  ON staged_feeding_production (farm_id);
CREATE INDEX idx_staged_feeding_production_due      ON staged_feeding_production (status, next_retry_at);
CREATE INDEX idx_staged_feeding_production_live_due ON staged_feeding_production (next_retry_at)
    WHERE status IN ('PENDING','RETRYING');

CREATE TABLE staged_feeding_event (
                                      id                   UUID NOT NULL,
                                      farm_id              UUID NOT NULL,
                                      payload_json         TEXT NOT NULL,
                                      status               VARCHAR(20) NOT NULL,
                                      validation_errors    VARCHAR(2000),
                                      submitted_by_user_id UUID,
                                      submitted_by_email   VARCHAR(100),
                                      submitted_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                                      last_attempt_at      TIMESTAMP,
                                      next_retry_at        TIMESTAMP,
                                      processed_at         TIMESTAMP,
                                      moved_feeding_id     UUID,
                                      retry_count          INT NOT NULL DEFAULT 0,
                                      version              BIGINT NOT NULL DEFAULT 0,
                                      PRIMARY KEY (id, submitted_at),
                                      CONSTRAINT fk_staged_feeding_event_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                      CONSTRAINT fk_staged_feeding_event_submitter FOREIGN KEY (submitted_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                                      CONSTRAINT fk_staged_feeding_event_moved FOREIGN KEY (moved_feeding_id) REFERENCES feeding_event (id) ON DELETE SET NULL,
                                      CONSTRAINT chk_staged_feeding_event_status CHECK (status IN ('PENDING','RETRYING','MOVED','FAILED')),
                                      CONSTRAINT chk_staged_feeding_event_retry_non_negative CHECK (retry_count >= 0)
) PARTITION BY RANGE (submitted_at);
CREATE INDEX idx_staged_feeding_event_status   ON staged_feeding_event (status);
CREATE INDEX idx_staged_feeding_event_farm_id  ON staged_feeding_event (farm_id);
CREATE INDEX idx_staged_feeding_event_due      ON staged_feeding_event (status, next_retry_at);
CREATE INDEX idx_staged_feeding_event_live_due ON staged_feeding_event (next_retry_at)
    WHERE status IN ('PENDING','RETRYING');

DO $$
BEGIN
    PERFORM ensure_partitions('staged_feeding_ingredient',  3, 2);
    PERFORM ensure_partitions('staged_feeding_formulation', 3, 2);
    PERFORM ensure_partitions('staged_feeding_production',  3, 2);
    PERFORM ensure_partitions('staged_feeding_event',       3, 2);
END $$;