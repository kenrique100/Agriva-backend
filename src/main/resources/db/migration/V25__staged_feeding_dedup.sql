
DELETE FROM staged_feeding_event
WHERE (id, submitted_at) NOT IN (
    SELECT id, MAX(submitted_at) FROM staged_feeding_event GROUP BY id
);

DELETE FROM staged_feeding_formulation
WHERE (id, submitted_at) NOT IN (
    SELECT id, MAX(submitted_at) FROM staged_feeding_formulation GROUP BY id
);

DELETE FROM staged_feeding_production
WHERE (id, submitted_at) NOT IN (
    SELECT id, MAX(submitted_at) FROM staged_feeding_production GROUP BY id
);

DELETE FROM staged_feeding_ingredient
WHERE (id, submitted_at) NOT IN (
    SELECT id, MAX(submitted_at) FROM staged_feeding_ingredient GROUP BY id
);