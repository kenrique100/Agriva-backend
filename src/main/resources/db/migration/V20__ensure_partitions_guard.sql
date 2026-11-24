
CREATE OR REPLACE FUNCTION ensure_partitions(
    parent_table TEXT,
    months_ahead INTEGER DEFAULT 3,
    months_back  INTEGER DEFAULT 0
) RETURNS void AS $$
DECLARE
m            INTEGER;
    start_date   DATE;
    end_date     DATE;
    partition_nm TEXT;
    relkind      "char";
BEGIN
    -- Look up the table's kind; raise a clear error if it does not exist.
SELECT c.relkind
INTO relkind
FROM pg_class c
         JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE c.relname = parent_table
  AND n.nspname = current_schema();

IF relkind IS NULL THEN
        RAISE EXCEPTION 'Table % does not exist in schema %',
            parent_table, current_schema();
END IF;

    IF relkind <> 'p' THEN
        RAISE NOTICE
            'Skipping ensure_partitions for %: not a partitioned table (relkind=%)',
            parent_table, relkind;
        RETURN;
END IF;

FOR m IN (-months_back)..months_ahead LOOP
        start_date   := DATE_TRUNC('month', CURRENT_DATE + (m || ' months')::INTERVAL)::DATE;
        end_date     := (start_date + INTERVAL '1 month')::DATE;
        partition_nm := parent_table || '_' || TO_CHAR(start_date, 'YYYY_MM');

EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
        partition_nm, parent_table, start_date, end_date);
END LOOP;
END;
$$ LANGUAGE plpgsql;