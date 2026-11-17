-- DemandForecast extends AuditableEntity (created_by, updated_by,
-- updated_at, deleted_at) AND declares its own @Version. V11 created the table
-- with only created_at + deleted_at, so Hibernate schema-validate refused to
-- boot on a missing [version] column.
--
-- The ALTER below adds all four missing columns in ONE statement. IF NOT EXISTS
-- makes it idempotent — safe on any DB that may have partially applied an
-- earlier version of this migration.

ALTER TABLE demand_forecast
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(50),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(50),
    ADD COLUMN IF NOT EXISTS version    BIGINT    NOT NULL DEFAULT 0;