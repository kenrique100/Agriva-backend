
INSERT INTO formulation_template
(id, farm_id, name, target_species, description, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000101',
     NULL,
     'AGRIVA_DEFAULT_CATFISH_GROWER',
     'CATFISH',
     'Agriva canonical catfish grower recipe. 15 lines, sums to 100%. '
         'Starting recipe, not nutritional advice — confirm with a feed '
         'nutritionist before production use. Clone to a farm template to customise.',
     NOW(), NOW());

INSERT INTO formulation_template_line
(id, template_id, ingredient_name, percentage)
VALUES
    ('00000000-0000-0000-0002-000000000001', '00000000-0000-0000-0000-000000000101', 'Soya beans',          35.0000),
    ('00000000-0000-0000-0002-000000000002', '00000000-0000-0000-0000-000000000101', 'Fish Meal',           20.0000),
    ('00000000-0000-0000-0002-000000000003', '00000000-0000-0000-0000-000000000101', 'Groundnuts',          10.0000),
    ('00000000-0000-0000-0002-000000000004', '00000000-0000-0000-0000-000000000101', 'Maize',                9.5000),
    ('00000000-0000-0000-0002-000000000005', '00000000-0000-0000-0000-000000000101', 'Blood Meal',           6.0000),
    ('00000000-0000-0000-0002-000000000006', '00000000-0000-0000-0000-000000000101', 'Cassava',              4.0000),
    ('00000000-0000-0000-0002-000000000007', '00000000-0000-0000-0000-000000000101', 'Diphosphate Calcium',  2.0000),
    ('00000000-0000-0000-0002-000000000008', '00000000-0000-0000-0000-000000000101', 'Bone Meal',            2.0000),
    ('00000000-0000-0000-0002-000000000009', '00000000-0000-0000-0000-000000000101', 'Marine Shell Flour',   1.5000),
    ('00000000-0000-0000-0002-000000000010', '00000000-0000-0000-0000-000000000101', 'Salt',                 0.5000),
    ('00000000-0000-0000-0002-000000000011', '00000000-0000-0000-0000-000000000101', 'Vitamin C',            0.5000),
    ('00000000-0000-0000-0002-000000000012', '00000000-0000-0000-0000-000000000101', 'Premix',               1.0000),
    ('00000000-0000-0000-0002-000000000013', '00000000-0000-0000-0000-000000000101', 'Palm Oil',             3.0000),
    ('00000000-0000-0000-0002-000000000014', '00000000-0000-0000-0000-000000000101', 'Anti-toxin',           0.0500),
    ('00000000-0000-0000-0002-000000000015', '00000000-0000-0000-0000-000000000101', 'Wheat Bran',           4.9500);

-- Backfill: existing catfish / tilapia forecasts that have no template yet.
-- Only touches live rows.
UPDATE demand_forecast
SET formula_template_id = '00000000-0000-0000-0000-000000000101'
WHERE formula_template_id IS NULL
  AND deleted_at IS NULL
  AND UPPER(species) LIKE '%CATFISH%';

UPDATE demand_forecast
SET formula_template_id = '00000000-0000-0000-0000-000000000100'
WHERE formula_template_id IS NULL
  AND deleted_at IS NULL
  AND UPPER(species) LIKE '%TILAPIA%';