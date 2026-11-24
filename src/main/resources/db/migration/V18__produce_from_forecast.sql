

-- Link a formulation back to the forecast that generated it.
ALTER TABLE formulation
    ADD COLUMN IF NOT EXISTS forecast_id UUID NULL;

ALTER TABLE formulation
    ADD CONSTRAINT fk_formulation_forecast
        FOREIGN KEY (forecast_id) REFERENCES demand_forecast(id)
            ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS ix_formulation_forecast_id
    ON formulation(forecast_id) WHERE forecast_id IS NOT NULL;

-- Client-supplied correlation token for idempotent "produce-feed" retries.
ALTER TABLE feed_batch
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_feed_batch_idempotency
    ON feed_batch(farm_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;