
CREATE TABLE fields (
                        id            UUID PRIMARY KEY,
                        farm_id       UUID NOT NULL,
                        name          VARCHAR(100) NOT NULL,
                        size_hectares NUMERIC(10,2),
                        soil_type     VARCHAR(50),
                        created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
                        updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
                        CONSTRAINT fk_fields_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                        CONSTRAINT ux_fields_farm_name UNIQUE (farm_id, name),
                        CONSTRAINT chk_fields_soil_type
                            CHECK (soil_type IS NULL OR soil_type IN (
                                                                      'SANDY','CLAY','LOAMY','SILTY','PEATY','CHALKY','SILT',
                                                                      'SANDY_LOAM','CLAY_LOAM','SILT_LOAM','SANDY_CLAY','SILTY_CLAY',
                                                                      'SANDY_CLAY_LOAM','SILTY_CLAY_LOAM','OTHER'))
);

CREATE TABLE crops (
                       id                       UUID PRIMARY KEY,
                       farm_id                  UUID NOT NULL,
                       name                     VARCHAR(100) NOT NULL,
                       variety                  VARCHAR(100),
                       expected_days_to_harvest INT,
                       created_at               TIMESTAMP NOT NULL DEFAULT NOW(),
                       updated_at               TIMESTAMP NOT NULL DEFAULT NOW(),
                       CONSTRAINT fk_crops_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE
);

CREATE TABLE crop_cycles (
                             id                    UUID PRIMARY KEY,
                             farm_id               UUID NOT NULL,
                             field_id              UUID NOT NULL,
                             crop_id               UUID NOT NULL,
                             planting_date         DATE NOT NULL,
                             expected_harvest_date DATE,
                             actual_harvest_date   DATE,
                             status                VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
                             notes                 TEXT,
                             deleted_at            TIMESTAMPTZ,
                             created_by            VARCHAR(50),
                             updated_by            VARCHAR(50),
                             created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
                             updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
                             version               BIGINT NOT NULL DEFAULT 0,
                             CONSTRAINT fk_cycles_farm  FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                             CONSTRAINT fk_cycles_field FOREIGN KEY (field_id) REFERENCES fields (id) ON DELETE CASCADE,
                             CONSTRAINT fk_cycles_crop  FOREIGN KEY (crop_id) REFERENCES crops (id) ON DELETE CASCADE,
                             CONSTRAINT chk_cycle_status
                                 CHECK (status IN ('PLANNED','PLANTED','GROWING','HARVESTED','FAILED'))
);
CREATE INDEX idx_cycles_farm ON crop_cycles (farm_id);

CREATE TABLE crop_activities (
                                 id                UUID PRIMARY KEY,
                                 farm_id           UUID NOT NULL,
                                 crop_cycle_id     UUID NOT NULL,
                                 logged_by_user_id UUID,
                                 activity_type     VARCHAR(50) NOT NULL,
                                 activity_date     DATE NOT NULL,
                                 notes             VARCHAR(500),
                                 linked_expense_id UUID,
                                 linked_task_id    UUID,
                                 created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
                                 CONSTRAINT fk_act_farm  FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                                 CONSTRAINT fk_act_cycle FOREIGN KEY (crop_cycle_id) REFERENCES crop_cycles (id) ON DELETE CASCADE,
                                 CONSTRAINT fk_act_user  FOREIGN KEY (logged_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_activities_cycle ON crop_activities (crop_cycle_id);

CREATE TABLE harvests (
                          id               UUID PRIMARY KEY,
                          farm_id          UUID NOT NULL,
                          crop_cycle_id    UUID NOT NULL,
                          harvest_date     DATE NOT NULL,
                          quantity         NUMERIC(19,4) NOT NULL,
                          unit             VARCHAR(20) NOT NULL DEFAULT 'KG',
                          quality_grade    VARCHAR(20),
                          linked_income_id UUID,
                          created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
                          CONSTRAINT fk_harv_farm  FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
                          CONSTRAINT fk_harv_cycle FOREIGN KEY (crop_cycle_id) REFERENCES crop_cycles (id) ON DELETE CASCADE
);