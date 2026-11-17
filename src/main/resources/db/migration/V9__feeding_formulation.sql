

ALTER TABLE ingredient
    ADD COLUMN density_kg_per_l NUMERIC(6,4);

CREATE TABLE formulation (
                             id                    UUID PRIMARY KEY,
                             farm_id               UUID          NOT NULL,
                             name                  VARCHAR(100)  NOT NULL,
                             target_species        VARCHAR(50)   NOT NULL,
                             mode                  VARCHAR(20)   NOT NULL,
                             target_quantity_kg    NUMERIC(19,4) NOT NULL,
                             target_cp_value       NUMERIC(6,2),
                             formula_template_id   UUID,
                             total_kg              NUMERIC(19,4) NOT NULL,
                             total_cost            NUMERIC(19,4),
                             algorithm_version     VARCHAR(50)   NOT NULL,
                             nutrient_summary_json TEXT,
                             created_by_user_id    UUID,
                             deleted_at            TIMESTAMPTZ,
                             created_by            VARCHAR(50),
                             updated_by            VARCHAR(50),
                             created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
                             updated_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
                             version               BIGINT        NOT NULL DEFAULT 0,

                             CONSTRAINT fk_formulation_farm
                                 FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                             CONSTRAINT fk_formulation_template
                                 FOREIGN KEY (formula_template_id) REFERENCES formulation_template (id) ON DELETE SET NULL,
                             CONSTRAINT fk_formulation_creator
                                 FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
                             CONSTRAINT chk_formulation_mode
                                 CHECK (mode IN ('ALGORITHM','CUSTOM')),
                             CONSTRAINT chk_formulation_target_kg
                                 CHECK (target_quantity_kg > 0),
                             CONSTRAINT chk_formulation_total_kg
                                 CHECK (total_kg >= 0)
);

CREATE INDEX idx_formulation_farm      ON formulation (farm_id);
CREATE INDEX idx_formulation_live      ON formulation (farm_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_formulation_created   ON formulation (farm_id, created_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE formulation_line (
                                  id              UUID PRIMARY KEY,
                                  formulation_id  UUID          NOT NULL,
                                  ingredient_id   UUID          NOT NULL,
                                  ingredient_name VARCHAR(100)  NOT NULL,
                                  quantity_kg     NUMERIC(19,4) NOT NULL,
                                  percentage      NUMERIC(6,4),
                                  unit_cost       NUMERIC(19,4),
                                  line_cost       NUMERIC(19,4),

                                  CONSTRAINT fk_formulation_line_formulation
                                      FOREIGN KEY (formulation_id) REFERENCES formulation (id) ON DELETE CASCADE,
                                  CONSTRAINT fk_formulation_line_ingredient
                                      FOREIGN KEY (ingredient_id) REFERENCES ingredient (id) ON DELETE RESTRICT,
                                  CONSTRAINT ux_formulation_line_ingredient
                                      UNIQUE (formulation_id, ingredient_id),
                                  CONSTRAINT chk_formulation_line_qty
                                      CHECK (quantity_kg > 0)
);

CREATE INDEX idx_formulation_line_formulation ON formulation_line (formulation_id);
CREATE INDEX idx_formulation_line_ingredient  ON formulation_line (ingredient_id);