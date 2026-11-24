ALTER TABLE formulation_template_line
    ADD COLUMN IF NOT EXISTS crude_protein_pct NUMERIC(7, 4) NULL;

COMMENT ON COLUMN formulation_template_line.crude_protein_pct
    IS 'Optional per-line crude-protein % for this template line. '
       'NULL = fall back to FeedCpProperties resolver at compute time.';