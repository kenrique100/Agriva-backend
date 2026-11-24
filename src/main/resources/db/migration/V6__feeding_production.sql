
CREATE TABLE feed_batch (
                            id                 UUID PRIMARY KEY,
                            farm_id            UUID NOT NULL,
                            formulation_id     UUID NOT NULL,
                            formulation_name   VARCHAR(100) NOT NULL,
                            produced_kg        NUMERIC(19,4) NOT NULL,
                            bag_size_kg        NUMERIC(10,4) NOT NULL,
                            number_of_bags     INT NOT NULL,
                            bulk_remainder_kg  NUMERIC(19,4) NOT NULL,
                            cost_total         NUMERIC(19,4),
                            production_date    DATE NOT NULL,
                            notes              VARCHAR(500),
                            created_by_user_id UUID,
                            created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
                            CONSTRAINT fk_feed_batch_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                            CONSTRAINT fk_feed_batch_formulation FOREIGN KEY (formulation_id) REFERENCES formulation (id) ON DELETE CASCADE,
                            CONSTRAINT fk_feed_batch_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                            CONSTRAINT chk_feed_batch_produced_positive     CHECK (produced_kg > 0),
                            CONSTRAINT chk_feed_batch_bag_size_positive     CHECK (bag_size_kg > 0),
                            CONSTRAINT chk_feed_batch_num_bags_non_negative CHECK (number_of_bags >= 0),
                            CONSTRAINT chk_feed_batch_remainder_non_negative CHECK (bulk_remainder_kg >= 0),
                            CONSTRAINT chk_feed_batch_remainder_lt_bag_size  CHECK (bulk_remainder_kg < bag_size_kg)
);
CREATE INDEX idx_feed_batch_farm_id           ON feed_batch (farm_id);
CREATE INDEX idx_feed_batch_farm_date_desc    ON feed_batch (farm_id, production_date DESC);
CREATE INDEX idx_feed_batch_formulation_id    ON feed_batch (formulation_id);
CREATE INDEX idx_feed_batch_farm_created_desc ON feed_batch (farm_id, created_at DESC);

CREATE TABLE farm_feed_store (
                                 id                  UUID PRIMARY KEY,
                                 farm_id             UUID NOT NULL,
                                 quantity_on_hand_kg NUMERIC(19,4) NOT NULL DEFAULT 0,
                                 version             BIGINT NOT NULL DEFAULT 0,
                                 created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
                                 updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
                                 CONSTRAINT fk_farm_feed_store_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                 CONSTRAINT ux_farm_feed_store_farm UNIQUE (farm_id),
                                 CONSTRAINT chk_farm_feed_store_qty_non_negative CHECK (quantity_on_hand_kg >= 0)
);

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