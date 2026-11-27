
UPDATE staged_task
SET moved_task_id = NULL
WHERE moved_task_id IN (
    SELECT id FROM tasks
    WHERE status = 'CANCELLED' OR deleted_at IS NOT NULL
);

-- 2. Hard-delete the dead rows.
DELETE FROM tasks
WHERE status = 'CANCELLED' OR deleted_at IS NOT NULL;