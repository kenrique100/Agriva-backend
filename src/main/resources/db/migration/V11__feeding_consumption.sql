

CREATE TABLE feeding_event (
                               id                    UUID PRIMARY KEY,
                               farm_id               UUID NOT NULL,
                               consumer_type         VARCHAR(20) NOT NULL,
                               consumer_id           UUID NOT NULL,
                               consumer_display_name VARCHAR(200) NOT NULL,
                               consumer_headcount    BIGINT NOT NULL,
                               consumer_stage        VARCHAR(30),
                               feed_batch_id         UUID,
                               quantity_kg           NUMERIC(19,4) NOT NULL,
                               fed_at                DATE NOT NULL,
                               notes                 VARCHAR(500),
                               created_by_user_id    UUID,
                               created_at            TIMESTAMP NOT NULL DEFAULT NOW(),

                               CONSTRAINT fk_feeding_event_farm    FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                               CONSTRAINT fk_feeding_event_batch   FOREIGN KEY (feed_batch_id) REFERENCES feed_batch (id) ON DELETE SET NULL,
                               CONSTRAINT fk_feeding_event_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                               CONSTRAINT chk_feeding_event_consumer_type
                                   CHECK (consumer_type IN ('FISHERY','POULTRY','LIVESTOCK','OTHER')),
                               CONSTRAINT chk_feeding_event_qty_positive           CHECK (quantity_kg > 0),
                               CONSTRAINT chk_feeding_event_headcount_non_negative CHECK (consumer_headcount >= 0)
);
CREATE INDEX idx_feeding_event_farm              ON feeding_event (farm_id);
CREATE INDEX idx_feeding_event_farm_date_desc    ON feeding_event (farm_id, fed_at DESC);
CREATE INDEX idx_feeding_event_consumer          ON feeding_event (farm_id, consumer_type, consumer_id);
CREATE INDEX idx_feeding_event_farm_created_desc ON feeding_event (farm_id, created_at DESC);