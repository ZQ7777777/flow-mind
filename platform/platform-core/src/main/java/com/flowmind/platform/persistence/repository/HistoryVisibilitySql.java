package com.flowmind.platform.persistence.repository;

/** SQL fragment shared by user-facing history queries. */
final class HistoryVisibilitySql {

    static final String PREDICATE =
            " AND NOT (h.action_type = 'CANCEL' AND ("
                    + "CASE WHEN json_valid(h.extra_json) "
                    + "THEN json_type(h.extra_json, '$.groupRejectTaskId') IS NOT NULL ELSE 0 END "
                    + "OR (EXISTS (SELECT 1 FROM process_task_group visible_group "
                    + "WHERE visible_group.id = h.task_group_id AND visible_group.group_type = 'OR_SIGN') "
                    + "AND EXISTS (SELECT 1 FROM process_history_task primary_history "
                    + "WHERE primary_history.instance_id = h.instance_id "
                    + "AND primary_history.operation_id = h.operation_id "
                    + "AND primary_history.task_group_id = h.task_group_id "
                    + "AND primary_history.active_task_id <> h.active_task_id "
                    + "AND primary_history.action_type <> 'CANCEL')))) ";

    private HistoryVisibilitySql() {
    }
}
