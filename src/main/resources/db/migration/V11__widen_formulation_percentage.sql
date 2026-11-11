-- NUMERIC(6,4) caps at 99.9999, so 100.0000 (a 100% single-line formulation)
-- overflows. Widen to NUMERIC(7,4), which holds 100.0000 fine.

ALTER TABLE formulation_line
ALTER COLUMN percentage TYPE NUMERIC(7,4);

ALTER TABLE formulation_template_line
ALTER COLUMN percentage          TYPE NUMERIC(7,4),
    ALTER COLUMN crude_protein_pct   TYPE NUMERIC(7,4);

COMMENT ON COLUMN formulation_line.percentage IS
    'Ingredient share of the formulation, 0 < x <= 100, 4 decimal places.';