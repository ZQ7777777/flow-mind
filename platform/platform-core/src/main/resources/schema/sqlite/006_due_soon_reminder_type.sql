PRAGMA foreign_keys = OFF;

CREATE TABLE process_reminder_record_due_soon_migration (
    id TEXT PRIMARY KEY,
    instance_id TEXT NOT NULL,
    task_id TEXT,
    reminder_type TEXT NOT NULL CHECK (reminder_type IN ('MANUAL', 'AUTO', 'DUE_SOON', 'TIMEOUT')),
    target_user_ids TEXT NOT NULL,
    message TEXT NOT NULL,
    reminder_status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (reminder_status IN ('PENDING', 'SENT', 'FAILED')),
    error_message TEXT,
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    sent_at TEXT,
    FOREIGN KEY (instance_id) REFERENCES process_instance (id)
);

INSERT INTO process_reminder_record_due_soon_migration (
    id,
    instance_id,
    task_id,
    reminder_type,
    target_user_ids,
    message,
    reminder_status,
    error_message,
    created_by,
    created_at,
    sent_at
)
SELECT
    id,
    instance_id,
    task_id,
    reminder_type,
    target_user_ids,
    message,
    reminder_status,
    error_message,
    created_by,
    created_at,
    sent_at
FROM process_reminder_record;

DROP TABLE process_reminder_record;
ALTER TABLE process_reminder_record_due_soon_migration RENAME TO process_reminder_record;

CREATE INDEX IF NOT EXISTS idx_process_reminder_record_task
    ON process_reminder_record (task_id, created_at);

CREATE INDEX IF NOT EXISTS idx_process_reminder_record_status
    ON process_reminder_record (reminder_status, created_at);

PRAGMA foreign_keys = ON;