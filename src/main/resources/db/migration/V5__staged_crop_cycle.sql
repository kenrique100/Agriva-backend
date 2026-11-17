

CREATE TABLE staged_crop_cycle (
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
                                   moved_cycle_id       UUID,
                                   retry_count          INT NOT NULL DEFAULT 0,
                                   version              BIGINT NOT NULL DEFAULT 0,
                                   PRIMARY KEY (id, submitted_at),
                                   CONSTRAINT fk_staged_crop_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                   CONSTRAINT chk_staged_crop_status CHECK (status IN ('PENDING','RETRYING','MOVED','FAILED'))
) PARTITION BY RANGE (submitted_at);
CREATE INDEX idx_staged_crop_due      ON staged_crop_cycle (status, next_retry_at);
CREATE INDEX idx_staged_crop_live_due ON staged_crop_cycle (next_retry_at)
    WHERE status IN ('PENDING','RETRYING');

DO $$ BEGIN PERFORM ensure_partitions('staged_crop_cycle', 3, 2); END $$;