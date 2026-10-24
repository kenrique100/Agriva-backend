
ALTER TABLE personnel
ALTER COLUMN total_hours_worked_last_30_days
        TYPE double precision
        USING total_hours_worked_last_30_days::double precision;

ALTER TABLE personnel
    ALTER COLUMN total_hours_worked_last_30_days
        SET DEFAULT 0.0;

ALTER TABLE personnel
    ALTER COLUMN total_hours_worked_last_30_days
        SET NOT NULL;

ALTER TABLE investment
ALTER COLUMN activated_at
        TYPE timestamp
        USING activated_at::timestamp;