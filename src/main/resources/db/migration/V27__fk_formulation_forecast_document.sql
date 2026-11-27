
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_formulation_forecast'
          AND conrelid = 'formulation'::regclass
    ) THEN
        EXECUTE 'COMMENT ON CONSTRAINT fk_formulation_forecast ON formulation IS '
             || quote_literal(
                    'ON DELETE RESTRICT is intentional. Deleting a referenced forecast '
                 || 'would orphan the formulation and its production batch. '
                 || 'Application-layer guard: ForecastServiceImpl.assertForecastNotReferenced. '
                 || 'Do not change to SET NULL — the forecast params are the recipe audit trail.');
END IF;
END $$;