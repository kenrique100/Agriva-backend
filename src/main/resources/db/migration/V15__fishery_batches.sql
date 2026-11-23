-- Tracks specific cohorts of fish in a pond
CREATE TABLE fishery_batch (
                               id                   UUID PRIMARY KEY,
                               farm_id              UUID NOT NULL,
                               pond_id              UUID NOT NULL,
                               species              VARCHAR(50) NOT NULL,
                               initial_head_count   BIGINT NOT NULL,
                               current_head_count   BIGINT NOT NULL,
                               start_date           DATE NOT NULL,
                               expected_harvest_date DATE,
                               status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                               created_at           TIMESTAMP NOT NULL DEFAULT NOW(),

                               CONSTRAINT fk_fb_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                               CONSTRAINT fk_fb_pond FOREIGN KEY (pond_id) REFERENCES ponds (id) ON DELETE CASCADE
);

-- Link forecasts and feeding events to specific batches
ALTER TABLE demand_forecast ADD COLUMN fishery_batch_id UUID;
ALTER TABLE feeding_event ADD COLUMN fishery_batch_id UUID;