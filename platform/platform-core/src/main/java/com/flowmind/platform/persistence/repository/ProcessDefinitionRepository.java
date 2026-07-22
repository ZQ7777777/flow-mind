package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.api.dto.ProcessDefinitionQuery;
import com.flowmind.platform.persistence.entity.ProcessDefinitionEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程定义主表仓储，只负责 process_definition 持久化访问。
 */
@Repository
public class ProcessDefinitionRepository {

    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final JdbcTemplate jdbcTemplate;

    public ProcessDefinitionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 根据id查询流程定义实体类
     * @param id
     * @return ProcessDefinitionEntity
     */
    public ProcessDefinitionEntity findById(String id) {
        List<ProcessDefinitionEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_definition WHERE id = ?",
                DefinitionRowMappers.DEFINITION, id);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 按流程编码读取唯一的全量激活定义，仅供新流程实例启动时选择版本。
     *
     * <p>已存在实例必须按其冻结的 definitionId 读取，不得调用此方法重新选版。</p>
     *
     * @param processCode 流程编码
     * @return 已发布、已激活且未开启灰度的定义；不存在时返回 {@code null}
     */
    public ProcessDefinitionEntity findActiveFullByProcessCode(String processCode) {
        List<ProcessDefinitionEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_definition WHERE process_code = ? "
                        + "AND definition_status = 'PUBLISHED' "
                        + "AND activation_status = 'ACTIVE' AND gray_status = 'OFF'",
                DefinitionRowMappers.DEFINITION, processCode);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 查询流程编码最大的版本号
     * @param processCode
     * @return 最大版本号
     */
    public Integer findMaxVersionByProcessCode(String processCode) {
        return jdbcTemplate.queryForObject(
                "SELECT MAX(version) FROM process_definition WHERE process_code = ?",
                Integer.class, processCode);
    }

    /**
     * 传入流程定义实体类，存到数据库的流程定义表中
     * @param entity
     * @return
     */
    public int insert(ProcessDefinitionEntity entity) {
        return jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, definition_status, "
                        + "activation_status, gray_status, gray_rule_config, archived_by, archived_at, remark, "
                        + "created_by, created_at, updated_by, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, COALESCE(?, 'DRAFT'), COALESCE(?, 'INACTIVE'), "
                        + "COALESCE(?, 'OFF'), ?, ?, ?, ?, ?, COALESCE(?, datetime('now')), ?, "
                        + "COALESCE(?, datetime('now')))",
                entity.getId(),
                entity.getProcessCode(),
                entity.getProcessName(),
                entity.getSystemCode(),
                entity.getVersion(),
                entity.getDefinitionStatus(),
                entity.getActivationStatus(),
                entity.getGrayStatus(),
                entity.getGrayRuleConfig(),
                entity.getArchivedBy(),
                DefinitionRowMappers.toDbString(entity.getArchivedAt()),
                entity.getRemark(),
                entity.getCreatedBy(),
                DefinitionRowMappers.toDbString(entity.getCreatedAt()),
                entity.getUpdatedBy(),
                DefinitionRowMappers.toDbString(entity.getUpdatedAt()));
    }

    /**
     * 更新流程定义的基础信息
     * @param entity
     * @return
     */
    public int updateBasicInfo(ProcessDefinitionEntity entity) {
        return jdbcTemplate.update("UPDATE process_definition "
                        + "SET process_name = ?, system_code = ?, remark = ?, updated_by = ?, "
                        + "updated_at = COALESCE(?, datetime('now')) WHERE id = ?",
                entity.getProcessName(),
                entity.getSystemCode(),
                entity.getRemark(),
                entity.getUpdatedBy(),
                DefinitionRowMappers.toDbString(entity.getUpdatedAt()),
                entity.getId());
    }

    /**
     * 将草稿定义发布为未激活版本。
     *
     * @param id        流程定义 ID
     * @param updatedBy 操作人 ID
     * @return 受影响行数，1 表示状态转换成功
     */
    public int publish(String id, String updatedBy) {
        return jdbcTemplate.update("UPDATE process_definition "
                        + "SET definition_status = 'PUBLISHED', updated_by = ?, updated_at = datetime('now') "
                        + "WHERE id = ? AND definition_status = 'DRAFT' "
                        + "AND activation_status = 'INACTIVE' AND gray_status = 'OFF'",
                updatedBy, id);
    }

    /**
     * 将已发布未激活的全量版本激活。
     *
     * @param id        流程定义 ID
     * @param updatedBy 操作人 ID
     * @return 受影响行数，1 表示状态转换成功
     */
    public int activateFull(String id, String updatedBy) {
        return jdbcTemplate.update("UPDATE process_definition "
                        + "SET activation_status = 'ACTIVE', updated_by = ?, updated_at = datetime('now') "
                        + "WHERE id = ? AND definition_status = 'PUBLISHED' "
                        + "AND activation_status = 'INACTIVE' AND gray_status = 'OFF'",
                updatedBy, id);
    }

    /**
     * 停用已发布且激活的全量版本。
     *
     * @param id        流程定义 ID
     * @param updatedBy 操作人 ID
     * @return 受影响行数，1 表示状态转换成功
     */
    public int deactivate(String id, String updatedBy) {
        return jdbcTemplate.update("UPDATE process_definition "
                        + "SET activation_status = 'INACTIVE', updated_by = ?, updated_at = datetime('now') "
                        + "WHERE id = ? AND definition_status = 'PUBLISHED' "
                        + "AND activation_status = 'ACTIVE' AND gray_status = 'OFF'",
                updatedBy, id);
    }

    /**
     * 停用同一流程编码下除目标定义外的全量激活版本。
     *
     * @param processCode         流程编码
     * @param excludeDefinitionId 本次即将激活的定义 ID
     * @param updatedBy           操作人 ID
     * @return 被停用的旧版本数量
     */
    public int deactivateActiveFullByProcessCode(String processCode, String excludeDefinitionId, String updatedBy) {
        return jdbcTemplate.update("UPDATE process_definition "
                        + "SET activation_status = 'INACTIVE', updated_by = ?, updated_at = datetime('now') "
                        + "WHERE process_code = ? AND id <> ? AND definition_status = 'PUBLISHED' "
                        + "AND activation_status = 'ACTIVE' AND gray_status = 'OFF'",
                updatedBy, processCode, excludeDefinitionId);
    }

    /**
     * 将已发布未激活的全量版本归档。
     *
     * @param id         流程定义 ID
     * @param archivedBy 归档操作人 ID
     * @param archivedAt 归档时间
     * @return 受影响行数，1 表示状态转换成功
     */
    public int archive(String id, String archivedBy, LocalDateTime archivedAt) {
        return jdbcTemplate.update("UPDATE process_definition "
                        + "SET definition_status = 'ARCHIVED', activation_status = 'INACTIVE', gray_status = 'OFF', "
                        + "archived_by = ?, archived_at = ?, updated_by = ?, updated_at = datetime('now') "
                        + "WHERE id = ? AND definition_status = 'PUBLISHED' "
                        + "AND activation_status = 'INACTIVE' AND gray_status = 'OFF'",
                archivedBy, DefinitionRowMappers.toDbString(archivedAt), archivedBy, id);
    }

    /**
     * 写入流程定义生命周期审计日志。
     *
     * @param id          审计日志 ID
     * @param instanceId  关联实例 ID，定义操作通常为空
     * @param operationId 幂等操作号
     * @param targetType  目标类型，典型值：DEFINITION
     * @param targetId    目标定义 ID
     * @param actionType  动作类型，典型值：DEFINITION_PUBLISH
     * @param operatorId  操作人 ID
     * @param detailJson  审计详情 JSON
     * @param createdAt   审计创建时间
     * @return 受影响行数
     */
    public int insertAuditLog(String id,
                              String instanceId,
                              String operationId,
                              String targetType,
                              String targetId,
                              String actionType,
                              String operatorId,
                              String detailJson,
                              LocalDateTime createdAt) {
        return jdbcTemplate.update("INSERT INTO process_audit_log "
                        + "(id, instance_id, operation_id, target_type, target_id, action_type, operator_id, "
                        + "detail_json, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, datetime('now')))",
                id, instanceId, operationId, targetType, targetId, actionType, operatorId, detailJson,
                DefinitionRowMappers.toDbString(createdAt));
    }

    /**
     * 刷新更新时间
     * @param id
     * @param updatedBy
     * @return
     */
    public int touchUpdated(String id, String updatedBy) {
        return jdbcTemplate.update("UPDATE process_definition "
                + "SET updated_by = ?, updated_at = datetime('now') WHERE id = ?", updatedBy, id);
    }

    /**
     * 从表格里删除流程定义
     * @param id
     * @return
     */
    public int deleteById(String id) {
        return jdbcTemplate.update("DELETE FROM process_definition WHERE id = ?", id);
    }

    /**
     * 删除流程定义Id对应的附件
     * @param definitionId
     * @return
     */
    public int deleteAttachmentsByDefinitionId(String definitionId) {
        return jdbcTemplate.update("DELETE FROM process_attachment WHERE instance_id IN "
                + "(SELECT id FROM process_instance WHERE definition_id = ?)", definitionId);
    }

    /**
     * 删除流程定义Id对应的已阅记录
     * @param definitionId
     * @return
     */
    public int deleteReadRecordsByDefinitionId(String definitionId) {
        return jdbcTemplate.update("DELETE FROM process_read_record WHERE instance_id IN "
                + "(SELECT id FROM process_instance WHERE definition_id = ?)", definitionId);
    }

    /**
     * 删除流程定义Id对应的历史任务
     * @param definitionId
     * @return
     */
    public int deleteHistoryTasksByDefinitionId(String definitionId) {
        return jdbcTemplate.update("DELETE FROM process_history_task WHERE instance_id IN "
                + "(SELECT id FROM process_instance WHERE definition_id = ?)", definitionId);
    }

    /**
     * 删除流程定义Id对应的活动任务
     * @param definitionId
     * @return
     */
    public int deleteActiveTasksByDefinitionId(String definitionId) {
        return jdbcTemplate.update("DELETE FROM process_active_task WHERE definition_id = ?", definitionId);
    }

    /**
     * 删除流程定义Id对应的任务组
     * @param definitionId
     * @return
     */
    public int deleteTaskGroupsByDefinitionId(String definitionId) {
        return jdbcTemplate.update("DELETE FROM process_task_group WHERE instance_id IN "
                + "(SELECT id FROM process_instance WHERE definition_id = ?)", definitionId);
    }

    /**
     * 删除流程定义Id对应的提醒/催办记录
     * @param definitionId
     * @return
     */
    public int deleteReminderRecordsByDefinitionId(String definitionId) {
        return jdbcTemplate.update("DELETE FROM process_reminder_record WHERE instance_id IN "
                + "(SELECT id FROM process_instance WHERE definition_id = ?)", definitionId);
    }

    /**
     * 删除流程定义Id对应的异常告警记录
     * @param definitionId
     * @return
     */
    public int deleteAlertRecordsByDefinitionId(String definitionId) {
        return jdbcTemplate.update("DELETE FROM process_alert_record WHERE instance_id IN "
                + "(SELECT id FROM process_instance WHERE definition_id = ?)", definitionId);
    }

    /**
     * 清空流程定义关联实例在审计日志中的引用，日志记录本身保留。
     *
     * @param definitionId 流程定义 ID
     * @return 受影响行数
     */
    public int clearAuditLogInstanceReferencesByDefinitionId(String definitionId) {
        return jdbcTemplate.update("UPDATE process_audit_log SET instance_id = NULL WHERE instance_id IN "
                + "(SELECT id FROM process_instance WHERE definition_id = ?)", definitionId);
    }

    /**
     * 清空流程定义关联实例在回调日志中的引用，日志记录本身保留。
     *
     * @param definitionId 流程定义 ID
     * @return 受影响行数
     */
    public int clearCallbackLogInstanceReferencesByDefinitionId(String definitionId) {
        return jdbcTemplate.update("UPDATE process_callback_log SET instance_id = NULL WHERE instance_id IN "
                + "(SELECT id FROM process_instance WHERE definition_id = ?)", definitionId);
    }

    /**
     * 删除流程定义关联的流程实例。
     *
     * @param definitionId 流程定义 ID
     * @return 受影响行数
     */
    public int deleteInstancesByDefinitionId(String definitionId) {
        return jdbcTemplate.update("DELETE FROM process_instance WHERE definition_id = ?", definitionId);
    }

    /**
     * 条件查询出来的总数，用于分页
     * @param query
     * @return
     */
    public long countByQuery(ProcessDefinitionQuery query) {
        QueryParts parts = buildQueryParts(query);
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_definition" + parts.whereClause,
                parts.args.toArray(), Long.class);
        return count == null ? 0L : count.longValue();
    }

    /**
     * 分页查询
     * @param query
     * @return
     */
    public List<ProcessDefinitionEntity> searchByQuery(ProcessDefinitionQuery query) {
        QueryParts parts = buildQueryParts(query);
        int pageNo = pageNo(query);
        int pageSize = pageSize(query);
        List<Object> args = new ArrayList<Object>(parts.args); //分页参数
        args.add(Integer.valueOf(pageSize));
        args.add(Integer.valueOf((pageNo - 1) * pageSize));
        //按更新时间、流程编码、版本号排序后得到的分页查询结果
        return jdbcTemplate.query("SELECT * FROM process_definition" + parts.whereClause
                        + " ORDER BY updated_at DESC, process_code ASC, version DESC LIMIT ? OFFSET ?",
                DefinitionRowMappers.DEFINITION, args.toArray());
    }

    /**
     * 查询流程定义Id对应的流程实例的数量
     * @param definitionId
     * @return
     */
    public boolean existsInstanceByDefinitionId(String definitionId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM process_instance WHERE definition_id = ?",
                Long.class, definitionId);
        return count != null && count.longValue() > 0L;
    }

    /**
     * 根据查询信息拼接查询条件，返回一个封装了SQL条件和参数的对象
     * @param query
     * @return QueryParts
     */
    private QueryParts buildQueryParts(ProcessDefinitionQuery query) {
        QueryParts parts = new QueryParts();
        if (query == null) {
            return parts;
        }
        if (hasText(query.getProcessCode())) {
            parts.add("process_code = ?", query.getProcessCode());
        }
        if (hasText(query.getProcessName())) {
            parts.add("process_name LIKE ?", "%" + query.getProcessName() + "%");
        }
        if (hasText(query.getSystemCode())) {
            parts.add("system_code = ?", query.getSystemCode());
        }
        if (query.getDefinitionStatus() != null) {
            parts.add("definition_status = ?", query.getDefinitionStatus().name());
        }
        if (query.getActivationStatus() != null) {
            parts.add("activation_status = ?", query.getActivationStatus().name());
        }
        if (query.getGrayStatus() != null) {
            parts.add("gray_status = ?", query.getGrayStatus().name());
        }
        return parts;
    }

    /**
     * 获取查询对应的分页参数
     * @param query
     * @return
     */
    private int pageNo(ProcessDefinitionQuery query) {
        if (query == null || query.getPageNo() == null || query.getPageNo().intValue() < 1) {
            return DEFAULT_PAGE_NO;
        }
        return query.getPageNo().intValue();
    }

    /**
     * 获取查询对应的分页参数
     * @param query
     * @return
     */
    private int pageSize(ProcessDefinitionQuery query) {
        if (query == null || query.getPageSize() == null || query.getPageSize().intValue() < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return query.getPageSize().intValue();
    }

    /**
     * 判断字符串是否是null
     * @param value
     * @return
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class QueryParts {
        private final List<String> conditions = new ArrayList<String>(); //所有查询条件
        private final List<Object> args = new ArrayList<Object>();  //每个？对应的参数
        private String whereClause = "";  //最终拼好的WHERE子句

        private void add(String condition, Object value) {
            conditions.add(condition);
            args.add(value);
            whereClause = " WHERE " + joinConditions();
        }

        /**
         * 拼接conditions里的所有查询条件，生成一个完整的条件查询语句
         * @return
         */
        private String joinConditions() {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < conditions.size(); i++) {
                if (i > 0) {
                    builder.append(" AND ");
                }
                builder.append(conditions.get(i));
            }
            return builder.toString();
        }
    }
}
