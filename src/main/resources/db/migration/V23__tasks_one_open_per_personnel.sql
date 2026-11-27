
DO $$
DECLARE
v RECORD;
BEGIN
FOR v IN
SELECT personnel_id,
       COUNT(*) AS c,
       STRING_AGG(id::text, ',' ORDER BY created_at) AS ids
FROM tasks
WHERE deleted_at IS NULL
  AND status IN ('PENDING','IN_PROGRESS','PAUSED','STOPPED')
GROUP BY personnel_id
HAVING COUNT(*) > 1
    LOOP
        RAISE EXCEPTION
            'V23: personnel % has % open tasks (ids: %). '
            'Finish, cancel, or soft-delete all but one before running this migration.',
            v.personnel_id, v.c, v.ids;
END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_tasks_one_open_per_personnel
    ON tasks (personnel_id)
    WHERE status IN ('PENDING','IN_PROGRESS','PAUSED','STOPPED')
    AND deleted_at IS NULL;