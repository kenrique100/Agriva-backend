-- 1. HR fields become optional (MASTER fills them in later via PUT /personnel/{id})
ALTER TABLE personnel ALTER COLUMN gender             DROP NOT NULL;
ALTER TABLE personnel ALTER COLUMN date_of_birth      DROP NOT NULL;
ALTER TABLE personnel ALTER COLUMN national_id        DROP NOT NULL;
ALTER TABLE personnel ALTER COLUMN department         DROP NOT NULL;
ALTER TABLE personnel ALTER COLUMN date_of_employment DROP NOT NULL;

-- 2. Drop orphan Personnel rows (userId NULL means old standalone flow)
DELETE FROM personnel WHERE user_id IS NULL;

-- 3. Drop the old FK so we can re-add with CASCADE
ALTER TABLE personnel DROP CONSTRAINT IF EXISTS fk_personnel_user;

-- 4. Backfill Personnel for every MASTER/SUB_USER that doesn't have one
INSERT INTO personnel (
    id, farm_id, user_id, name, gender, date_of_birth, email,
    national_id, phone_number, department, date_of_employment,
    status, status_change_timestamp, working_days_count,
    current_period_start_date, total_productive_days,
    created_at, updated_at
)
SELECT
    gen_random_uuid(),
    u.farm_id,
    u.id,
    COALESCE(NULLIF(u.username, ''), split_part(u.email, '@', 1)),
    u.gender,
    u.date_of_birth,
    u.email,
    NULL,
    u.phone_number,
    'ADMINISTRATION',
    COALESCE(u.created_at::date, CURRENT_DATE),
    'ACTIVE',
    COALESCE(u.created_at, NOW()),
    0,
    CURRENT_DATE,
    0,
    COALESCE(u.created_at, NOW()),
    COALESCE(u.updated_at, NOW())
FROM users u
WHERE u.role IN ('MASTER', 'SUB_USER')
  AND u.farm_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM personnel p WHERE p.user_id = u.id);

-- 5. Give every backfilled personnel an initial ACTIVE status history row
INSERT INTO personnel_status_history (id, personnel_id, status, start_timestamp, created_at)
SELECT gen_random_uuid(), p.id, 'ACTIVE', p.created_at, p.created_at
FROM personnel p
WHERE NOT EXISTS (
    SELECT 1 FROM personnel_status_history h WHERE h.personnel_id = p.id
);

-- 6. Enforce 1:1 with User
ALTER TABLE personnel ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE personnel ADD CONSTRAINT ux_personnel_user_id UNIQUE (user_id);

-- 7. Re-add FK with ON DELETE CASCADE
ALTER TABLE personnel
    ADD CONSTRAINT fk_personnel_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;