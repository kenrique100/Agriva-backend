CREATE TABLE auth_events (
                             id           UUID PRIMARY KEY,
                             user_id      UUID,
                             event_type   VARCHAR(50) NOT NULL,
                             ip_address   VARCHAR(45),
                             user_agent   VARCHAR(500),
                             metadata     JSONB,
                             created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_auth_events_user    ON auth_events (user_id, created_at DESC);
CREATE INDEX idx_auth_events_created ON auth_events (created_at DESC);