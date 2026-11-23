
CREATE TABLE IF NOT EXISTS ingredient_stock_adjustment (
                                                           id                    UUID PRIMARY KEY,
                                                           farm_id               UUID          NOT NULL,
                                                           ingredient_id         UUID          NOT NULL,
                                                           previous_quantity     NUMERIC(19, 4) NOT NULL,
    new_quantity          NUMERIC(19, 4) NOT NULL,
    delta                 NUMERIC(19, 4) NOT NULL,
    unit                  VARCHAR(10)   NOT NULL,
    reason                VARCHAR(500)  NOT NULL,
    adjustment_type       VARCHAR(20)   NOT NULL,
    performed_by_user_id  UUID          NULL,
    performed_at          TIMESTAMP     NOT NULL
    );

CREATE INDEX IF NOT EXISTS ix_isa_farm_ingredient
    ON ingredient_stock_adjustment(farm_id, ingredient_id);
CREATE INDEX IF NOT EXISTS ix_isa_performed_at
    ON ingredient_stock_adjustment(performed_at DESC);

CREATE TABLE IF NOT EXISTS feed_store_adjustment (
                                                     id                    UUID PRIMARY KEY,
                                                     farm_id               UUID          NOT NULL,
                                                     previous_quantity_kg  NUMERIC(19, 4) NOT NULL,
    new_quantity_kg       NUMERIC(19, 4) NOT NULL,
    delta_kg              NUMERIC(19, 4) NOT NULL,
    reason                VARCHAR(500)  NOT NULL,
    adjustment_type       VARCHAR(20)   NOT NULL,
    performed_by_user_id  UUID          NULL,
    performed_at          TIMESTAMP     NOT NULL
    );

CREATE INDEX IF NOT EXISTS ix_fsa_farm
    ON feed_store_adjustment(farm_id);
CREATE INDEX IF NOT EXISTS ix_fsa_performed_at
    ON feed_store_adjustment(performed_at DESC);