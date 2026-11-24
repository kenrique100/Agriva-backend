-- Enforce unique formulation names per farm (only for live/non-deleted rows).
-- This prevents NonUniqueResultException in staging validators and ensures
-- a farm cannot have two formulations with the same name.
CREATE UNIQUE INDEX IF NOT EXISTS ux_formulation_farm_name_live
    ON formulation (farm_id, LOWER(name))
    WHERE deleted_at IS NULL;