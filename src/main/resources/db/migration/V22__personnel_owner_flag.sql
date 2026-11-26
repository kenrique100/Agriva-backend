
ALTER TABLE personnel
    ADD COLUMN IF NOT EXISTS is_farm_owner BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE personnel p
SET is_farm_owner = TRUE
WHERE EXISTS (
    SELECT 1 FROM users u
    WHERE u.id = p.user_id
      AND u.role = 'MASTER'
);

CREATE INDEX IF NOT EXISTS idx_personnel_staff
    ON personnel (farm_id)
    WHERE is_farm_owner = FALSE AND deleted_at IS NULL;