
ALTER TABLE staged_feeding_ingredient
    ADD COLUMN batch_id   UUID,
    ADD COLUMN row_number INTEGER;

CREATE INDEX idx_staged_feeding_ingredient_batch
    ON staged_feeding_ingredient (farm_id, batch_id)
    WHERE batch_id IS NOT NULL;

COMMENT ON COLUMN staged_feeding_ingredient.batch_id   IS 'Groups rows from one bulk upload; NULL for single-submit.';
COMMENT ON COLUMN staged_feeding_ingredient.row_number IS 'Original 1-based Excel row (header row excluded); NULL for single-submit.';