

CREATE OR REPLACE FUNCTION ensure_partitions(
    parent_table TEXT,
    months_ahead INTEGER DEFAULT 3,
    months_back  INTEGER DEFAULT 0
) RETURNS void AS $$
DECLARE
m             INTEGER;
    start_date    DATE;
    end_date      DATE;
    partition_nm  TEXT;
BEGIN
FOR m IN (-months_back)..months_ahead LOOP
        start_date   := DATE_TRUNC('month', CURRENT_DATE + (m || ' months')::INTERVAL)::DATE;
        end_date     := (start_date + INTERVAL '1 month')::DATE;
        partition_nm := parent_table || '_' || TO_CHAR(start_date, 'YYYY_MM');
EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
        partition_nm, parent_table, start_date, end_date
        );
END LOOP;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION drop_partitions_older_than(
    parent_table TEXT,
    keep_days    INTEGER
) RETURNS INTEGER AS $$
DECLARE
partition_rec  RECORD;
    cutoff         DATE;
    dropped        INTEGER := 0;
    p_date         DATE;
    suffix         TEXT;
BEGIN
    cutoff := CURRENT_DATE - keep_days;

FOR partition_rec IN
SELECT inhrelid::regclass::text AS name
FROM pg_inherits
WHERE inhparent = parent_table::regclass
    LOOP
        suffix := substring(partition_rec.name FROM '(\d{4}_\d{2})$');
CONTINUE WHEN suffix IS NULL;

BEGIN
            p_date := TO_DATE(suffix, 'YYYY_MM');
EXCEPTION WHEN OTHERS THEN
            CONTINUE;
END;

        IF p_date < cutoff THEN
            EXECUTE format('DROP TABLE IF EXISTS %s', partition_rec.name);
            dropped := dropped + 1;
END IF;
END LOOP;

RETURN dropped;
END;
$$ LANGUAGE plpgsql;