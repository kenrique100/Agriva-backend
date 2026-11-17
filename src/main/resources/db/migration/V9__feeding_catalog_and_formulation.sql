

CREATE TABLE ingredient (
                            id                UUID PRIMARY KEY,
                            farm_id           UUID NOT NULL,
                            name              VARCHAR(100) NOT NULL,
                            unit              VARCHAR(10) NOT NULL,
                            cost_per_unit     NUMERIC(19,4),
                            currency          VARCHAR(3),
                            supplier          VARCHAR(200),
                            density_kg_per_l  NUMERIC(6,4),
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
                            CONSTRAINT chk_ingredient_currency CHECK (currency IS NULL OR char_length(currency) = 3)
);
CREATE UNIQUE INDEX ux_ingredient_farm_name_live
    ON ingredient (farm_id, LOWER(name)) WHERE deleted_at IS NULL;
CREATE INDEX idx_ingredient_farm_id ON ingredient (farm_id);
CREATE INDEX idx_ingredient_live    ON ingredient (farm_id) WHERE deleted_at IS NULL;

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

CREATE TABLE formulation_template (
                                      id             UUID PRIMARY KEY,
                                      farm_id        UUID,
                                      name           VARCHAR(100) NOT NULL,
                                      target_species VARCHAR(50),
                                      description    VARCHAR(500),
                                      deleted_at     TIMESTAMPTZ,
                                      created_by     VARCHAR(50),
                                      updated_by     VARCHAR(50),
                                      created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
                                      updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
                                      version        BIGINT NOT NULL DEFAULT 0,

                                      CONSTRAINT fk_formulation_template_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX ux_ft_farm_name_live
    ON formulation_template (farm_id, LOWER(name))
    WHERE farm_id IS NOT NULL AND deleted_at IS NULL;
CREATE UNIQUE INDEX ux_ft_system_name_live
    ON formulation_template (LOWER(name))
    WHERE farm_id IS NULL AND deleted_at IS NULL;
CREATE INDEX idx_ft_farm_id ON formulation_template (farm_id);

CREATE TABLE formulation_template_line (
                                           id                UUID PRIMARY KEY,
                                           template_id       UUID NOT NULL,
                                           ingredient_name   VARCHAR(100) NOT NULL,
                                           percentage        NUMERIC(7,4) NOT NULL,
                                           crude_protein_pct NUMERIC(7,4),

                                           CONSTRAINT fk_ftl_template FOREIGN KEY (template_id) REFERENCES formulation_template (id) ON DELETE CASCADE,
                                           CONSTRAINT chk_ftl_percentage CHECK (percentage > 0 AND percentage <= 100),
                                           CONSTRAINT chk_ftl_cp
                                               CHECK (crude_protein_pct IS NULL OR (crude_protein_pct >= 0 AND crude_protein_pct <= 100))
);
CREATE INDEX idx_ftl_template ON formulation_template_line (template_id);

CREATE TABLE demand_forecast (
                                 id                    UUID PRIMARY KEY,
                                 farm_id               UUID NOT NULL,
                                 species               VARCHAR(50) NOT NULL,
                                 number_of_head        BIGINT NOT NULL,
                                 initial_weight_kg     NUMERIC(19,4) NOT NULL,
                                 target_weight_kg      NUMERIC(19,4) NOT NULL,
                                 water_temperature_c   NUMERIC(6,2),
                                 target_cp_value       NUMERIC(6,2),
                                 formula_template_id   UUID,
                                 total_feed_kg         NUMERIC(19,4) NOT NULL,
                                 total_days            INT NOT NULL,
                                 average_daily_feed_kg NUMERIC(19,4) NOT NULL,
                                 algorithm_version     VARCHAR(50) NOT NULL,
                                 created_by_user_id    UUID,
                                 deleted_at            TIMESTAMPTZ,
                                 created_by            VARCHAR(50),
                                 updated_by            VARCHAR(50),
                                 created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
                                 updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
                                 version               BIGINT NOT NULL DEFAULT 0,

                                 CONSTRAINT fk_df_farm     FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                 CONSTRAINT fk_df_template FOREIGN KEY (formula_template_id) REFERENCES formulation_template (id) ON DELETE SET NULL,
                                 CONSTRAINT fk_df_creator  FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                                 CONSTRAINT chk_df_head_positive           CHECK (number_of_head > 0),
                                 CONSTRAINT chk_df_weights_positive        CHECK (initial_weight_kg > 0 AND target_weight_kg > initial_weight_kg),
                                 CONSTRAINT chk_df_total_feed_non_negative CHECK (total_feed_kg >= 0),
                                 CONSTRAINT chk_df_total_days_positive     CHECK (total_days > 0)
);
CREATE INDEX idx_df_farm         ON demand_forecast (farm_id);
CREATE INDEX idx_df_farm_created ON demand_forecast (farm_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_df_species      ON demand_forecast (farm_id, species) WHERE deleted_at IS NULL;

CREATE TABLE demand_forecast_stage (
                                       id                        UUID PRIMARY KEY,
                                       forecast_id               UUID NOT NULL,
                                       stage_index               INT NOT NULL,
                                       stage_name                VARCHAR(30) NOT NULL,
                                       start_weight_kg           NUMERIC(19,4) NOT NULL,
                                       end_weight_kg             NUMERIC(19,4) NOT NULL,
                                       days_in_stage             INT NOT NULL,
                                       feed_consumed_kg          NUMERIC(19,4) NOT NULL,
                                       daily_feed_requirement_kg NUMERIC(19,4) NOT NULL,
                                       feeding_times_per_day     INT NOT NULL,

                                       CONSTRAINT fk_dfs_forecast FOREIGN KEY (forecast_id) REFERENCES demand_forecast (id) ON DELETE CASCADE,
                                       CONSTRAINT ux_dfs_forecast_index UNIQUE (forecast_id, stage_index),
                                       CONSTRAINT chk_dfs_days_positive    CHECK (days_in_stage > 0),
                                       CONSTRAINT chk_dfs_feed_non_negative CHECK (feed_consumed_kg >= 0)
);
CREATE INDEX idx_dfs_forecast ON demand_forecast_stage (forecast_id);

CREATE TABLE formulation (
                             id                    UUID PRIMARY KEY,
                             farm_id               UUID NOT NULL,
                             name                  VARCHAR(100) NOT NULL,
                             target_species        VARCHAR(50) NOT NULL,
                             mode                  VARCHAR(20) NOT NULL,
                             target_quantity_kg    NUMERIC(19,4) NOT NULL,
                             target_cp_value       NUMERIC(6,2),
                             formula_template_id   UUID,
                             total_kg              NUMERIC(19,4) NOT NULL,
                             total_cost            NUMERIC(19,4),
                             algorithm_version     VARCHAR(50) NOT NULL,
                             nutrient_summary_json TEXT,
                             created_by_user_id    UUID,
                             deleted_at            TIMESTAMPTZ,
                             created_by            VARCHAR(50),
                             updated_by            VARCHAR(50),
                             created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
                             updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
                             version               BIGINT NOT NULL DEFAULT 0,

                             CONSTRAINT fk_formulation_farm     FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                             CONSTRAINT fk_formulation_template FOREIGN KEY (formula_template_id) REFERENCES formulation_template (id) ON DELETE SET NULL,
                             CONSTRAINT fk_formulation_creator  FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                             CONSTRAINT chk_formulation_mode      CHECK (mode IN ('ALGORITHM','CUSTOM')),
                             CONSTRAINT chk_formulation_target_kg CHECK (target_quantity_kg > 0),
                             CONSTRAINT chk_formulation_total_kg  CHECK (total_kg >= 0)
);
CREATE INDEX idx_formulation_farm    ON formulation (farm_id);
CREATE INDEX idx_formulation_live    ON formulation (farm_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_formulation_created ON formulation (farm_id, created_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE formulation_line (
                                  id              UUID PRIMARY KEY,
                                  formulation_id  UUID NOT NULL,
                                  ingredient_id   UUID NOT NULL,
                                  ingredient_name VARCHAR(100) NOT NULL,
                                  quantity_kg     NUMERIC(19,4) NOT NULL,
                                  percentage      NUMERIC(7,4),
                                  unit_cost       NUMERIC(19,4),
                                  line_cost       NUMERIC(19,4),

                                  CONSTRAINT fk_formulation_line_formulation FOREIGN KEY (formulation_id) REFERENCES formulation (id) ON DELETE CASCADE,
                                  CONSTRAINT fk_formulation_line_ingredient  FOREIGN KEY (ingredient_id)  REFERENCES ingredient (id)  ON DELETE CASCADE,
                                  CONSTRAINT ux_formulation_line_ingredient  UNIQUE (formulation_id, ingredient_id),
                                  CONSTRAINT chk_formulation_line_qty        CHECK (quantity_kg > 0)
);
CREATE INDEX idx_formulation_line_formulation ON formulation_line (formulation_id);
CREATE INDEX idx_formulation_line_ingredient  ON formulation_line (ingredient_id);

-- ---------- Seed system template ----------
INSERT INTO formulation_template
(id, farm_id, name, target_species, description, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000100',
     NULL,
     'AGRIVA_DEFAULT_TILAPIA_GROWER',
     'TILAPIA',
     'Agriva canonical tilapia grower recipe. 15 lines, sums to 100%. Use as a starting point; clone to a farm template to customise.',
     NOW(), NOW());

INSERT INTO formulation_template_line
(id, template_id, ingredient_name, percentage, crude_protein_pct)
VALUES
    ('00000000-0000-0000-0001-000000000001', '00000000-0000-0000-0000-000000000100', 'Soya beans',          30.0000, 44.0000),
    ('00000000-0000-0000-0001-000000000002', '00000000-0000-0000-0000-000000000100', 'Groundnuts',          10.0000, 45.0000),
    ('00000000-0000-0000-0001-000000000003', '00000000-0000-0000-0000-000000000100', 'Blood Meal',           5.0000, 80.0000),
    ('00000000-0000-0000-0001-000000000004', '00000000-0000-0000-0000-000000000100', 'Fish Meal',           10.0000, 65.0000),
    ('00000000-0000-0000-0001-000000000005', '00000000-0000-0000-0000-000000000100', 'Maize',               20.0000,  9.0000),
    ('00000000-0000-0000-0001-000000000006', '00000000-0000-0000-0000-000000000100', 'Cassava',             10.0000,  2.0000),
    ('00000000-0000-0000-0001-000000000007', '00000000-0000-0000-0000-000000000100', 'Diphosphate Calcium',  2.0000,  0.0000),
    ('00000000-0000-0000-0001-000000000008', '00000000-0000-0000-0000-000000000100', 'Bone Meal',            2.0000,  0.0000),
    ('00000000-0000-0000-0001-000000000009', '00000000-0000-0000-0000-000000000100', 'Marine Shell Flour',   2.0000,  0.0000),
    ('00000000-0000-0000-0001-000000000010', '00000000-0000-0000-0000-000000000100', 'Salt',                 0.5000,  0.0000),
    ('00000000-0000-0000-0001-000000000011', '00000000-0000-0000-0000-000000000100', 'Vitamin C',            0.5000,  0.0000),
    ('00000000-0000-0000-0001-000000000012', '00000000-0000-0000-0000-000000000100', 'Premix',               1.0000,  0.0000),
    ('00000000-0000-0000-0001-000000000013', '00000000-0000-0000-0000-000000000100', 'Palm Oil',             2.0000,  0.0000),
    ('00000000-0000-0000-0001-000000000014', '00000000-0000-0000-0000-000000000100', 'Anti-toxin',           0.0500,  0.0000),
    ('00000000-0000-0000-0001-000000000015', '00000000-0000-0000-0000-000000000100', 'Wheat Bran',           4.9500,  0.0000);