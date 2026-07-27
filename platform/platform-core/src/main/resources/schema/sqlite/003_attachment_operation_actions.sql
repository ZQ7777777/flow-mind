BEGIN;

CREATE TABLE process_operation_record_attachment_actions (
    id TEXT PRIMARY KEY,
    operation_id TEXT NOT NULL UNIQUE,
    instance_id TEXT,
    task_id TEXT,
    action_type TEXT NOT NULL
        CHECK (action_type IN ('START', 'START_AND_SUBMIT', 'SEND', 'APPROVE', 'UPDATE_VARIABLES', 'REJECT', 'RETURN', 'WITHDRAW', 'DIRECT_SEND', 'TRANSFER', 'ADD_SIGN', 'JUMP', 'TERMINATE', 'CLAIM', 'UNCLAIM', 'CANCEL', 'FORCE_COMPLETE', 'ARCHIVE', 'ENABLE_GRAY', 'DISABLE_GRAY', 'REMIND', 'ALERT_HANDLE', 'ATTACHMENT_UPLOAD', 'ATTACHMENT_DELETE', 'DEFINITION_CREATE', 'DEFINITION_SAVE_GRAPH', 'DEFINITION_COPY', 'DEFINITION_DELETE', 'DEFINITION_VALIDATE_FOR_PUBLISH', 'DEFINITION_PUBLISH', 'DEFINITION_ACTIVATE', 'DEFINITION_DEACTIVATE', 'DEFINITION_ARCHIVE', 'DEFINITION_ENABLE_GRAY', 'DEFINITION_DISABLE_GRAY')),
    operator_id TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    operation_status TEXT NOT NULL DEFAULT 'PROCESSING'
        CHECK (operation_status IN ('PROCESSING', 'SUCCESS', 'FAILED')),
    result_json TEXT,
    error_code TEXT,
    processing_expires_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

INSERT INTO process_operation_record_attachment_actions
    (id, operation_id, instance_id, task_id, action_type, operator_id, request_hash,
     operation_status, result_json, error_code, processing_expires_at, expires_at, created_at, updated_at)
SELECT id, operation_id, instance_id, task_id, action_type, operator_id, request_hash,
       operation_status, result_json, error_code, processing_expires_at, expires_at, created_at, updated_at
FROM process_operation_record;

DROP TABLE process_operation_record;

ALTER TABLE process_operation_record_attachment_actions RENAME TO process_operation_record;

CREATE INDEX idx_process_operation_record_instance
    ON process_operation_record (instance_id, created_at);

CREATE INDEX idx_process_operation_record_task
    ON process_operation_record (task_id, created_at);

CREATE INDEX idx_process_operation_record_status_expires
    ON process_operation_record (operation_status, processing_expires_at);

CREATE INDEX idx_process_operation_record_expires
    ON process_operation_record (expires_at);

CREATE TABLE process_audit_log_attachment_actions (
    id TEXT PRIMARY KEY,
    instance_id TEXT,
    operation_id TEXT,
    target_type TEXT NOT NULL CHECK (target_type IN ('DEFINITION', 'INSTANCE', 'TASK', 'ATTACHMENT')),
    target_id TEXT NOT NULL,
    action_type TEXT NOT NULL
        CHECK (action_type IN ('START', 'SEND', 'APPROVE', 'REJECT', 'RETURN', 'WITHDRAW', 'DIRECT_SEND', 'TRANSFER', 'ADD_SIGN', 'JUMP', 'TERMINATE', 'CLAIM', 'UNCLAIM', 'CANCEL', 'FORCE_COMPLETE', 'ARCHIVE', 'ENABLE_GRAY', 'DISABLE_GRAY', 'REMIND', 'ALERT_HANDLE', 'ATTACHMENT_UPLOAD', 'ATTACHMENT_DELETE', 'DEFINITION_CREATE', 'DEFINITION_SAVE_GRAPH', 'DEFINITION_COPY', 'DEFINITION_DELETE', 'DEFINITION_VALIDATE_FOR_PUBLISH', 'DEFINITION_PUBLISH', 'DEFINITION_ACTIVATE', 'DEFINITION_DEACTIVATE', 'DEFINITION_ARCHIVE', 'DEFINITION_ENABLE_GRAY', 'DEFINITION_DISABLE_GRAY')),
    operator_id TEXT NOT NULL,
    detail_json TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (instance_id) REFERENCES process_instance (id)
);

INSERT INTO process_audit_log_attachment_actions
    (id, instance_id, operation_id, target_type, target_id, action_type, operator_id, detail_json, created_at)
SELECT id, instance_id, operation_id, target_type, target_id, action_type, operator_id, detail_json, created_at
FROM process_audit_log;

DROP TABLE process_audit_log;

ALTER TABLE process_audit_log_attachment_actions RENAME TO process_audit_log;

CREATE INDEX idx_process_audit_log_instance_created
    ON process_audit_log (instance_id, created_at);

CREATE INDEX idx_process_audit_log_operation
    ON process_audit_log (operation_id);

COMMIT;
