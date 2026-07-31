package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.ProcessTaskGroupEntity;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/** 运行时四张主表的 JDBC 行映射集合。 */
final class RuntimeRowMappers {

    static final RowMapper<ProcessInstanceEntity> INSTANCE = new RowMapper<ProcessInstanceEntity>() {
        @Override
        public ProcessInstanceEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            ProcessInstanceEntity entity = new ProcessInstanceEntity();
            entity.setId(resultSet.getString("id"));
            entity.setDefinitionId(resultSet.getString("definition_id"));
            entity.setAttachmentConfigId(resultSet.getString("attachment_config_id"));
            entity.setProcessCode(resultSet.getString("process_code"));
            entity.setProcessName(resultSet.getString("process_name"));
            entity.setVersion(Integer.valueOf(resultSet.getInt("version")));
            entity.setInstanceTitle(resultSet.getString("instance_title"));
            entity.setBusinessKey(resultSet.getString("business_key"));
            entity.setStarterUserId(resultSet.getString("starter_user_id"));
            entity.setStarterUserName(resultSet.getString("starter_user_name"));
            entity.setStarterDeptId(resultSet.getString("starter_dept_id"));
            entity.setCurrentNodeCodes(resultSet.getString("current_node_codes"));
            entity.setVariablesJson(resultSet.getString("variables_json"));
            entity.setInstanceStatus(resultSet.getString("instance_status"));
            entity.setStartedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("started_at")));
            entity.setEndedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("ended_at")));
            return entity;
        }
    };

    static final RowMapper<ProcessActiveTaskEntity> ACTIVE_TASK = new RowMapper<ProcessActiveTaskEntity>() {
        @Override
        public ProcessActiveTaskEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            ProcessActiveTaskEntity entity = new ProcessActiveTaskEntity();
            entity.setId(resultSet.getString("id"));
            entity.setInstanceId(resultSet.getString("instance_id"));
            entity.setDefinitionId(resultSet.getString("definition_id"));
            entity.setNodeCode(resultSet.getString("node_code"));
            entity.setCandidateUserIds(resultSet.getString("candidate_user_ids"));
            entity.setAssigneeUserId(resultSet.getString("assignee_user_id"));
            entity.setAssigneeUserName(resultSet.getString("assignee_user_name"));
            entity.setDelegateFromUserId(resultSet.getString("delegate_from_user_id"));
            entity.setDelegateFromUserName(resultSet.getString("delegate_from_user_name"));
            entity.setTaskStatus(resultSet.getString("task_status"));
            entity.setTaskGroupId(resultSet.getString("task_group_id"));
            entity.setBranchKey(resultSet.getString("branch_key"));
            entity.setLockVersion(Long.valueOf(resultSet.getLong("lock_version")));
            entity.setCreatedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("created_at")));
            entity.setDueAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("due_at")));
            return entity;
        }
    };

    static final RowMapper<ProcessHistoryTaskEntity> HISTORY_TASK = new RowMapper<ProcessHistoryTaskEntity>() {
        @Override
        public ProcessHistoryTaskEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            ProcessHistoryTaskEntity entity = new ProcessHistoryTaskEntity();
            entity.setId(resultSet.getString("id"));
            entity.setInstanceId(resultSet.getString("instance_id"));
            entity.setOperationId(resultSet.getString("operation_id"));
            entity.setActiveTaskId(resultSet.getString("active_task_id"));
            entity.setNodeCode(resultSet.getString("node_code"));
            entity.setTaskGroupId(resultSet.getString("task_group_id"));
            entity.setBranchKey(resultSet.getString("branch_key"));
            entity.setAssigneeUserId(resultSet.getString("assignee_user_id"));
            entity.setAssigneeUserName(resultSet.getString("assignee_user_name"));
            entity.setDelegateFromUserId(resultSet.getString("delegate_from_user_id"));
            entity.setDelegateFromUserName(resultSet.getString("delegate_from_user_name"));
            entity.setHandleType(resultSet.getString("handle_type"));
            entity.setActionType(resultSet.getString("action_type"));
            entity.setCommentText(resultSet.getString("comment_text"));
            entity.setVariablesSnapshot(resultSet.getString("variables_snapshot"));
            entity.setStartedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("started_at")));
            entity.setCompletedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("completed_at")));
            entity.setExtraJson(resultSet.getString("extra_json"));
            return entity;
        }
    };

    static final RowMapper<ProcessTaskGroupEntity> TASK_GROUP = new RowMapper<ProcessTaskGroupEntity>() {
        @Override
        public ProcessTaskGroupEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            ProcessTaskGroupEntity entity = new ProcessTaskGroupEntity();
            entity.setId(resultSet.getString("id"));
            entity.setInstanceId(resultSet.getString("instance_id"));
            entity.setNodeCode(resultSet.getString("node_code"));
            entity.setJoinNodeCode(resultSet.getString("join_node_code"));
            entity.setParentGroupId(resultSet.getString("parent_group_id"));
            entity.setParentBranchKey(resultSet.getString("parent_branch_key"));
            entity.setGroupType(resultSet.getString("group_type"));
            entity.setTotalCount(Integer.valueOf(resultSet.getInt("total_count")));
            entity.setCompletedCount(Integer.valueOf(resultSet.getInt("completed_count")));
            entity.setBranchStateJson(resultSet.getString("branch_state_json"));
            entity.setGroupStatus(resultSet.getString("group_status"));
            entity.setLockVersion(Long.valueOf(resultSet.getLong("lock_version")));
            entity.setCreatedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("created_at")));
            entity.setCompletedAt(DefinitionRowMappers.toLocalDateTime(resultSet.getString("completed_at")));
            return entity;
        }
    };

    private RuntimeRowMappers() {
    }
}
