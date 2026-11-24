
DELETE FROM staged_feeding_event
WHERE (id, submitted_at) NOT IN (
    SELECT id, MAX(submitted_at) FROM staged_feeding_event GROUP BY id
);

DELETE FROM staged_feeding_formulation
WHERE (id, submitted_at) NOT IN (
    SELECT id, MAX(submitted_at) FROM staged_feeding_formulation GROUP BY id
);

DELETE FROM staged_feeding_production
WHERE (id, submitted_at) NOT IN (
    SELECT id, MAX(submitted_at) FROM staged_feeding_production GROUP BY id
);

DELETE FROM staged_feeding_ingredient
WHERE (id, submitted_at) NOT IN (
    SELECT id, MAX(submitted_at) FROM staged_feeding_ingredient GROUP BY id
);

ALTER TABLE staged_feeding_event
    ADD COLUMN IF NOT EXISTS batch_id   UUID NULL,
    ADD COLUMN IF NOT EXISTS row_number INTEGER NULL;

ALTER TABLE staged_feeding_formulation
    ADD COLUMN IF NOT EXISTS batch_id   UUID NULL,
    ADD COLUMN IF NOT EXISTS row_number INTEGER NULL;

ALTER TABLE staged_feeding_production
    ADD COLUMN IF NOT EXISTS batch_id   UUID NULL,
    ADD COLUMN IF NOT EXISTS row_number INTEGER NULL;

CREATE INDEX IF NOT EXISTS ix_staged_feeding_event_batch
    ON staged_feeding_event(batch_id) WHERE batch_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_staged_feeding_formulation_batch
    ON staged_feeding_formulation(batch_id) WHERE batch_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_staged_feeding_production_batch
    ON staged_feeding_production(batch_id) WHERE batch_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_staged_feeding_ingredient_batch
    ON staged_feeding_ingredient(batch_id) WHERE batch_id IS NOT NULL;