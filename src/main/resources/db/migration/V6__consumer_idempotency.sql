

CREATE TABLE consumer_processed_events (
                                           event_id       UUID NOT NULL,
                                           consumer_group VARCHAR(100) NOT NULL,
                                           processed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                           PRIMARY KEY (event_id, consumer_group)
);
CREATE INDEX idx_consumer_processed_at ON consumer_processed_events (processed_at);