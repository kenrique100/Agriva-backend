
CREATE TABLE ingredient (
                            id                UUID PRIMARY KEY,
                            farm_id           UUID NOT NULL,
                            name              VARCHAR(100) NOT NULL,
                            unit              VARCHAR(10) NOT NULL,
                            cost_per_unit     NUMERIC(19,4),
                            currency          VARCHAR(3),
                            supplier          VARCHAR(200),
                            density_kg_per_l  NUMERIC(6,4),
                            crude_protein_pct NUMERIC(7,4),
                            deleted_at        TIMESTAMPTZ,
                            created_by        VARCHAR(50),
                            updated_by        VARCHAR(50),
                            created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
                            updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
                            version           BIGINT NOT NULL DEFAULT 0,
                            CONSTRAINT fk_ingredient_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                            CONSTRAINT chk_ingredient_name_non_empty CHECK (LENGTH(TRIM(name)) > 0),
                            CONSTRAINT chk_ingredient_unit CHECK (unit IN ('KG','L','EACH')),
                            CONSTRAINT chk_ingredient_cost_positive CHECK (cost_per_unit IS NULL OR cost_per_unit >= 0),
                            CONSTRAINT chk_ingredient_currency CHECK (currency IS NULL OR char_length(currency) = 3),
                            CONSTRAINT chk_ingredient_cp_range CHECK (crude_protein_pct IS NULL
                                OR (crude_protein_pct >= 0 AND crude_protein_pct <= 100))
);
CREATE UNIQUE INDEX ux_ingredient_farm_name_live ON ingredient (farm_id, LOWER(name)) WHERE deleted_at IS NULL;
CREATE INDEX idx_ingredient_farm_id ON ingredient (farm_id);
CREATE INDEX idx_ingredient_live    ON ingredient (farm_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_ingredient_cp_present ON ingredient (farm_id) WHERE deleted_at IS NULL AND crude_protein_pct IS NOT NULL;

CREATE TABLE ingredient_stock (
                                  id                  UUID PRIMARY KEY,
                                  farm_id             UUID NOT NULL,
                                  ingredient_id       UUID NOT NULL,
                                  quantity_on_hand    NUMERIC(19,4) NOT NULL DEFAULT 0,
                                  unit                VARCHAR(10) NOT NULL,
                                  last_replenished_at TIMESTAMP,
                                  version             BIGINT NOT NULL DEFAULT 0,
                                  CONSTRAINT fk_ingredient_stock_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                  CONSTRAINT fk_ingredient_stock_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredient (id) ON DELETE CASCADE,
                                  CONSTRAINT ux_ingredient_stock_farm_ingredient UNIQUE (farm_id, ingredient_id),
                                  CONSTRAINT chk_ingredient_stock_qty_non_negative CHECK (quantity_on_hand >= 0),
                                  CONSTRAINT chk_ingredient_stock_unit CHECK (unit IN ('KG','L','EACH'))
);
CREATE INDEX idx_ingredient_stock_farm       ON ingredient_stock (farm_id);
CREATE INDEX idx_ingredient_stock_ingredient ON ingredient_stock (ingredient_id);