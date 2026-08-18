BEGIN IMMEDIATE;

ALTER TABLE process_node RENAME TO process_node_before_notice;

CREATE TABLE process_node (
    id TEXT PRIMARY KEY,
    definition_id TEXT NOT NULL,
    node_code TEXT NOT NULL,
    node_name TEXT NOT NULL,
    node_type TEXT NOT NULL
        CHECK (node_type IN ('START', 'USER_TASK', 'NOTICE', 'EXCLUSIVE_GATEWAY', 'PARALLEL_SPLIT_GATEWAY', 'PARALLEL_JOIN_GATEWAY', 'END')),
    paired_gateway_code TEXT,
    approver_rule_type TEXT
        CHECK (approver_rule_type IS NULL OR approver_rule_type IN ('USER', 'STARTER', 'DEPARTMENT', 'ROLE', 'ROLE_IN_DEPARTMENT', 'APPROVER_EXPRESSION')),
    approver_rule_config TEXT,
    multi_instance_mode TEXT NOT NULL DEFAULT 'SINGLE'
        CHECK (multi_instance_mode IN ('SINGLE', 'OR_SIGN', 'COUNTERSIGN')),
    listener_config TEXT,
    timeout_config TEXT,
    reminder_config TEXT,
    notice_config TEXT,
    position_x REAL,
    position_y REAL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (definition_id) REFERENCES process_definition (id),
    UNIQUE (definition_id, node_code)
);

INSERT INTO process_node (
    id, definition_id, node_code, node_name, node_type, paired_gateway_code,
    approver_rule_type, approver_rule_config, multi_instance_mode, listener_config,
    timeout_config, reminder_config, notice_config, position_x, position_y, sort_order
)
SELECT
    id, definition_id, node_code, node_name, node_type, paired_gateway_code,
    approver_rule_type, approver_rule_config, multi_instance_mode, listener_config,
    timeout_config, reminder_config, NULL, position_x, position_y, sort_order
FROM process_node_before_notice;

DROP TABLE process_node_before_notice;

CREATE INDEX idx_process_node_definition_sort
    ON process_node (definition_id, sort_order);

COMMIT;
