PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS process_definition (
    id TEXT PRIMARY KEY,
    process_code TEXT NOT NULL,
    process_name TEXT NOT NULL,
    system_code TEXT NOT NULL,
    version INTEGER NOT NULL,
    definition_status TEXT NOT NULL DEFAULT 'DRAFT'
        CHECK (definition_status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    activation_status TEXT NOT NULL DEFAULT 'INACTIVE'
        CHECK (activation_status IN ('INACTIVE', 'ACTIVE')),
    gray_status TEXT NOT NULL DEFAULT 'OFF'
        CHECK (gray_status IN ('OFF', 'ON')),
    gray_rule_config TEXT,
    archived_by TEXT,
    archived_at TEXT,
    remark TEXT,
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_by TEXT,
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (process_code, version),
    CHECK (gray_status = 'OFF' OR gray_rule_config IS NOT NULL),
    CHECK (definition_status <> 'ARCHIVED' OR activation_status = 'INACTIVE')
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_process_definition_active_full
    ON process_definition (process_code)
    WHERE definition_status = 'PUBLISHED'
      AND activation_status = 'ACTIVE'
      AND gray_status = 'OFF';

CREATE INDEX IF NOT EXISTS idx_process_definition_query
    ON process_definition (system_code, process_code, definition_status, activation_status);

CREATE TABLE IF NOT EXISTS process_node (
    id TEXT PRIMARY KEY,
    definition_id TEXT NOT NULL,
    node_code TEXT NOT NULL,
    node_name TEXT NOT NULL,
    node_type TEXT NOT NULL
        CHECK (node_type IN ('START', 'USER_TASK', 'EXCLUSIVE_GATEWAY', 'PARALLEL_SPLIT_GATEWAY', 'PARALLEL_JOIN_GATEWAY', 'END')),
    paired_gateway_code TEXT,
    approver_rule_type TEXT
        CHECK (approver_rule_type IS NULL OR approver_rule_type IN ('USER', 'STARTER', 'DEPARTMENT', 'ROLE', 'ROLE_IN_DEPARTMENT', 'APPROVER_EXPRESSION')),
    approver_rule_config TEXT,
    multi_instance_mode TEXT NOT NULL DEFAULT 'SINGLE'
        CHECK (multi_instance_mode IN ('SINGLE', 'OR_SIGN', 'COUNTERSIGN')),
    listener_config TEXT,
    timeout_config TEXT,
    reminder_config TEXT,
    position_x REAL,
    position_y REAL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (definition_id) REFERENCES process_definition (id),
    UNIQUE (definition_id, node_code)
);

CREATE INDEX IF NOT EXISTS idx_process_node_definition_sort
    ON process_node (definition_id, sort_order);

CREATE TABLE IF NOT EXISTS process_edge (
    id TEXT PRIMARY KEY,
    definition_id TEXT NOT NULL,
    edge_code TEXT NOT NULL,
    source_node_code TEXT NOT NULL,
    target_node_code TEXT NOT NULL,
    condition_expression TEXT,
    default_edge INTEGER NOT NULL DEFAULT 0 CHECK (default_edge IN (0, 1)),
    sort_order INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (definition_id) REFERENCES process_definition (id),
    UNIQUE (definition_id, edge_code)
);

CREATE INDEX IF NOT EXISTS idx_process_edge_source_sort
    ON process_edge (definition_id, source_node_code, sort_order);

CREATE UNIQUE INDEX IF NOT EXISTS uk_process_edge_default_source
    ON process_edge (definition_id, source_node_code)
    WHERE default_edge = 1;

CREATE TABLE IF NOT EXISTS process_form_field (
    id TEXT PRIMARY KEY,
    definition_id TEXT NOT NULL,
    field_code TEXT NOT NULL,
    field_name TEXT NOT NULL,
    field_type TEXT NOT NULL,
    control_type TEXT NOT NULL,
    required INTEGER NOT NULL DEFAULT 0 CHECK (required IN (0, 1)),
    validation_rule TEXT,
    default_value TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (definition_id) REFERENCES process_definition (id),
    UNIQUE (definition_id, field_code)
);

CREATE INDEX IF NOT EXISTS idx_process_form_field_sort
    ON process_form_field (definition_id, sort_order);

CREATE TABLE IF NOT EXISTS process_attachment_template (
    id TEXT PRIMARY KEY,
    attachment_code TEXT NOT NULL,
    template_version INTEGER NOT NULL,
    attachment_name TEXT NOT NULL,
    description TEXT,
    allowed_extensions TEXT NOT NULL,
    max_size_bytes INTEGER NOT NULL CHECK (max_size_bytes > 0),
    template_status TEXT NOT NULL DEFAULT 'ENABLED'
        CHECK (template_status IN ('ENABLED', 'DISABLED')),
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_by TEXT,
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (attachment_code, template_version)
);

CREATE INDEX IF NOT EXISTS idx_process_attachment_template_code
    ON process_attachment_template (attachment_code, template_status);

CREATE TABLE IF NOT EXISTS process_definition_attachment_config (
    id TEXT PRIMARY KEY,
    attachment_config_id TEXT NOT NULL,
    definition_id TEXT NOT NULL,
    config_status TEXT NOT NULL DEFAULT 'DRAFT'
        CHECK (config_status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    activated_at TEXT,
    attachment_template_id TEXT NOT NULL,
    attachment_code TEXT NOT NULL,
    required INTEGER NOT NULL DEFAULT 0 CHECK (required IN (0, 1)),
    min_count INTEGER NOT NULL DEFAULT 0 CHECK (min_count >= 0),
    max_count INTEGER CHECK (max_count IS NULL OR max_count >= min_count),
    applicable_node_codes TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_by TEXT,
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (definition_id) REFERENCES process_definition (id),
    FOREIGN KEY (attachment_template_id) REFERENCES process_attachment_template (id),
    UNIQUE (attachment_config_id, attachment_template_id),
    UNIQUE (attachment_config_id, attachment_code),
    CHECK (required = 0 OR min_count >= 1)
);

CREATE INDEX IF NOT EXISTS idx_process_attachment_config_definition_status
    ON process_definition_attachment_config (definition_id, config_status);

CREATE INDEX IF NOT EXISTS idx_process_attachment_config_group
    ON process_definition_attachment_config (attachment_config_id, sort_order);

CREATE TABLE IF NOT EXISTS process_instance (
    id TEXT PRIMARY KEY,
    definition_id TEXT NOT NULL,
    attachment_config_id TEXT,
    process_code TEXT NOT NULL,
    process_name TEXT NOT NULL,
    version INTEGER NOT NULL,
    instance_title TEXT NOT NULL,
    business_key TEXT,
    starter_user_id TEXT NOT NULL,
    starter_user_name TEXT NOT NULL,
    starter_dept_id TEXT,
    current_node_codes TEXT,
    variables_json TEXT,
    instance_status TEXT NOT NULL DEFAULT 'NOT_STARTED'
        CHECK (instance_status IN ('NOT_STARTED', 'RUNNING', 'COMPLETED', 'ARCHIVED', 'TERMINATED')),
    started_at TEXT,
    ended_at TEXT,
    FOREIGN KEY (definition_id) REFERENCES process_definition (id)
);

CREATE INDEX IF NOT EXISTS idx_process_instance_starter_status
    ON process_instance (starter_user_id, instance_status, started_at);

CREATE INDEX IF NOT EXISTS idx_process_instance_definition_status
    ON process_instance (definition_id, instance_status);

CREATE INDEX IF NOT EXISTS idx_process_instance_business_key
    ON process_instance (business_key);

CREATE TABLE IF NOT EXISTS process_task_group (
    id TEXT PRIMARY KEY,
    instance_id TEXT,
    node_code TEXT NOT NULL,
    join_node_code TEXT,
    parent_group_id TEXT,
    parent_branch_key TEXT,
    group_type TEXT NOT NULL
        CHECK (group_type IN ('OR_SIGN', 'COUNTERSIGN', 'PARALLEL_GATEWAY')),
    total_count INTEGER NOT NULL CHECK (total_count >= 0),
    completed_count INTEGER NOT NULL DEFAULT 0 CHECK (completed_count >= 0),
    branch_state_json TEXT,
    group_status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (group_status IN ('ACTIVE', 'COMPLETED', 'CANCELED')),
    lock_version INTEGER NOT NULL DEFAULT 0 CHECK (lock_version >= 0),
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    completed_at TEXT,
    FOREIGN KEY (instance_id) REFERENCES process_instance (id),
    FOREIGN KEY (parent_group_id) REFERENCES process_task_group (id)
);

CREATE INDEX IF NOT EXISTS idx_process_task_group_instance_status
    ON process_task_group (instance_id, group_status);

CREATE INDEX IF NOT EXISTS idx_process_task_group_join
    ON process_task_group (id, join_node_code, group_status);

CREATE TABLE IF NOT EXISTS process_active_task (
    id TEXT PRIMARY KEY,
    instance_id TEXT NOT NULL,
    definition_id TEXT NOT NULL,
    node_code TEXT NOT NULL,
    candidate_user_ids TEXT,
    assignee_user_id TEXT,
    assignee_user_name TEXT,
    delegate_from_user_id TEXT,
    task_status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (task_status IN ('ACTIVE', 'CLAIMED', 'COMPLETED', 'CANCELED')),
    task_group_id TEXT,
    branch_key TEXT,
    lock_version INTEGER NOT NULL DEFAULT 0 CHECK (lock_version >= 0),
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    due_at TEXT,
    FOREIGN KEY (instance_id) REFERENCES process_instance (id),
    FOREIGN KEY (definition_id) REFERENCES process_definition (id),
    FOREIGN KEY (task_group_id) REFERENCES process_task_group (id)
);

CREATE INDEX IF NOT EXISTS idx_process_active_task_assignee
    ON process_active_task (assignee_user_id, task_status, created_at);

CREATE INDEX IF NOT EXISTS idx_process_active_task_instance
    ON process_active_task (instance_id, task_status);

CREATE INDEX IF NOT EXISTS idx_process_active_task_due
    ON process_active_task (due_at, task_status);

CREATE INDEX IF NOT EXISTS idx_process_active_task_group_branch
    ON process_active_task (task_group_id, branch_key);

CREATE TABLE IF NOT EXISTS process_history_task (
    id TEXT PRIMARY KEY,
    instance_id TEXT NOT NULL,
    operation_id TEXT,
    active_task_id TEXT NOT NULL,
    node_code TEXT NOT NULL,
    task_group_id TEXT,
    branch_key TEXT,
    assignee_user_id TEXT,
    assignee_user_name TEXT,
    delegate_from_user_id TEXT,
    delegate_from_user_name TEXT,
    handle_type TEXT NOT NULL DEFAULT 'NORMAL'
        CHECK (handle_type IN ('NORMAL', 'DELEGATE', 'TRANSFER', 'ADMIN_PROXY')),
    action_type TEXT NOT NULL
        CHECK (action_type IN ('START', 'SEND', 'APPROVE', 'REJECT', 'RETURN', 'WITHDRAW', 'DIRECT_SEND', 'TRANSFER', 'ADD_SIGN', 'JUMP', 'TERMINATE', 'CLAIM', 'UNCLAIM', 'CANCEL', 'FORCE_COMPLETE', 'ARCHIVE', 'ENABLE_GRAY', 'DISABLE_GRAY', 'REMIND', 'ALERT_HANDLE')),
    comment_text TEXT,
    variables_snapshot TEXT,
    started_at TEXT,
    completed_at TEXT NOT NULL DEFAULT (datetime('now')),
    extra_json TEXT,
    FOREIGN KEY (instance_id) REFERENCES process_instance (id),
    UNIQUE (active_task_id, action_type, operation_id)
);

CREATE INDEX IF NOT EXISTS idx_process_history_task_instance_completed
    ON process_history_task (instance_id, completed_at);

CREATE INDEX IF NOT EXISTS idx_process_history_task_assignee_completed
    ON process_history_task (assignee_user_id, completed_at);

CREATE TABLE IF NOT EXISTS process_read_record (
    id TEXT PRIMARY KEY,
    instance_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    user_name TEXT NOT NULL,
    read_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (instance_id) REFERENCES process_instance (id),
    UNIQUE (instance_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_process_read_record_user
    ON process_read_record (user_id, read_at);

CREATE TABLE IF NOT EXISTS process_attachment (
    id TEXT PRIMARY KEY,
    instance_id TEXT NOT NULL,
    task_id TEXT,
    owner_type TEXT NOT NULL CHECK (owner_type IN ('INSTANCE', 'TASK')),
    attachment_code TEXT NOT NULL,
    field_code TEXT,
    file_name TEXT NOT NULL,
    content_type TEXT,
    size_bytes INTEGER NOT NULL CHECK (size_bytes >= 0),
    storage_key TEXT NOT NULL,
    uploaded_by TEXT NOT NULL,
    uploaded_at TEXT NOT NULL DEFAULT (datetime('now')),
    deleted INTEGER NOT NULL DEFAULT 0 CHECK (deleted IN (0, 1)),
    deleted_by TEXT,
    deleted_at TEXT,
    FOREIGN KEY (instance_id) REFERENCES process_instance (id)
);

CREATE INDEX IF NOT EXISTS idx_process_attachment_instance_owner
    ON process_attachment (instance_id, owner_type, deleted);

CREATE INDEX IF NOT EXISTS idx_process_attachment_task
    ON process_attachment (task_id, deleted);

CREATE TABLE IF NOT EXISTS process_audit_log (
    id TEXT PRIMARY KEY,
    instance_id TEXT,
    operation_id TEXT,
    target_type TEXT NOT NULL CHECK (target_type IN ('DEFINITION', 'INSTANCE', 'TASK', 'ATTACHMENT')),
    target_id TEXT NOT NULL,
    action_type TEXT NOT NULL
        CHECK (action_type IN ('START', 'SEND', 'APPROVE', 'REJECT', 'RETURN', 'WITHDRAW', 'DIRECT_SEND', 'TRANSFER', 'ADD_SIGN', 'JUMP', 'TERMINATE', 'CLAIM', 'UNCLAIM', 'CANCEL', 'FORCE_COMPLETE', 'ARCHIVE', 'ENABLE_GRAY', 'DISABLE_GRAY', 'REMIND', 'ALERT_HANDLE', 'DEFINITION_CREATE', 'DEFINITION_SAVE_GRAPH', 'DEFINITION_COPY', 'DEFINITION_DELETE', 'DEFINITION_VALIDATE_FOR_PUBLISH', 'DEFINITION_PUBLISH', 'DEFINITION_ACTIVATE', 'DEFINITION_DEACTIVATE', 'DEFINITION_ARCHIVE', 'DEFINITION_ENABLE_GRAY', 'DEFINITION_DISABLE_GRAY')),
    operator_id TEXT NOT NULL,
    detail_json TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (instance_id) REFERENCES process_instance (id)
);

CREATE INDEX IF NOT EXISTS idx_process_audit_log_instance_created
    ON process_audit_log (instance_id, created_at);

CREATE INDEX IF NOT EXISTS idx_process_audit_log_operation
    ON process_audit_log (operation_id);

CREATE TABLE IF NOT EXISTS process_callback_log (
    id TEXT PRIMARY KEY,
    event_id TEXT NOT NULL UNIQUE,
    instance_id TEXT,
    operation_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    action_type TEXT NOT NULL
        CHECK (action_type IN ('START', 'SEND', 'APPROVE', 'REJECT', 'RETURN', 'WITHDRAW', 'DIRECT_SEND', 'TRANSFER', 'ADD_SIGN', 'JUMP', 'TERMINATE', 'CLAIM', 'UNCLAIM', 'CANCEL', 'FORCE_COMPLETE', 'ARCHIVE', 'ENABLE_GRAY', 'DISABLE_GRAY', 'REMIND', 'ALERT_HANDLE')),
    payload_json TEXT NOT NULL,
    callback_status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (callback_status IN ('PENDING', 'SUCCESS', 'FAILED')),
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    last_error TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (instance_id) REFERENCES process_instance (id)
);

CREATE INDEX IF NOT EXISTS idx_process_callback_log_status
    ON process_callback_log (callback_status, created_at);

CREATE INDEX IF NOT EXISTS idx_process_callback_log_operation
    ON process_callback_log (operation_id, event_type);

CREATE TABLE IF NOT EXISTS process_reminder_record (
    id TEXT PRIMARY KEY,
    instance_id TEXT NOT NULL,
    task_id TEXT,
    reminder_type TEXT NOT NULL CHECK (reminder_type IN ('MANUAL', 'AUTO', 'TIMEOUT')),
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

CREATE INDEX IF NOT EXISTS idx_process_reminder_record_task
    ON process_reminder_record (task_id, created_at);

CREATE INDEX IF NOT EXISTS idx_process_reminder_record_status
    ON process_reminder_record (reminder_status, created_at);

CREATE TABLE IF NOT EXISTS process_alert_record (
    id TEXT PRIMARY KEY,
    instance_id TEXT,
    task_id TEXT,
    alert_type TEXT NOT NULL CHECK (alert_type IN ('TASK_TIMEOUT', 'CALLBACK_FAILED', 'ACTION_EXCEPTION')),
    severity TEXT NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    alert_status TEXT NOT NULL DEFAULT 'OPEN'
        CHECK (alert_status IN ('OPEN', 'HANDLED', 'IGNORED')),
    detail_json TEXT NOT NULL,
    handled_by TEXT,
    handled_at TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (instance_id) REFERENCES process_instance (id)
);

CREATE INDEX IF NOT EXISTS idx_process_alert_record_status
    ON process_alert_record (alert_status, severity, created_at);

CREATE INDEX IF NOT EXISTS idx_process_alert_record_instance
    ON process_alert_record (instance_id, created_at);

CREATE TABLE IF NOT EXISTS process_operation_record (
    id TEXT PRIMARY KEY,
    operation_id TEXT NOT NULL UNIQUE,
    instance_id TEXT,
    task_id TEXT,
    action_type TEXT NOT NULL
        CHECK (action_type IN ('START', 'SEND', 'APPROVE', 'REJECT', 'RETURN', 'WITHDRAW', 'DIRECT_SEND', 'TRANSFER', 'ADD_SIGN', 'JUMP', 'TERMINATE', 'CLAIM', 'UNCLAIM', 'CANCEL', 'FORCE_COMPLETE', 'ARCHIVE', 'ENABLE_GRAY', 'DISABLE_GRAY', 'REMIND', 'ALERT_HANDLE', 'DEFINITION_CREATE', 'DEFINITION_SAVE_GRAPH', 'DEFINITION_COPY', 'DEFINITION_DELETE', 'DEFINITION_VALIDATE_FOR_PUBLISH', 'DEFINITION_PUBLISH', 'DEFINITION_ACTIVATE', 'DEFINITION_DEACTIVATE', 'DEFINITION_ARCHIVE', 'DEFINITION_ENABLE_GRAY', 'DEFINITION_DISABLE_GRAY')),
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

CREATE INDEX IF NOT EXISTS idx_process_operation_record_instance
    ON process_operation_record (instance_id, created_at);

CREATE INDEX IF NOT EXISTS idx_process_operation_record_task
    ON process_operation_record (task_id, created_at);

CREATE INDEX IF NOT EXISTS idx_process_operation_record_status_expires
    ON process_operation_record (operation_status, processing_expires_at);

CREATE INDEX IF NOT EXISTS idx_process_operation_record_expires
    ON process_operation_record (expires_at);
