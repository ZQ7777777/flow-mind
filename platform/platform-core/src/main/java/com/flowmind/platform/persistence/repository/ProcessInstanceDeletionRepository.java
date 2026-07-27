package com.flowmind.platform.persistence.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 流程实例物理删除的级联持久化操作。
 *
 * <p>本仓储只负责数据库内的运行数据和日志引用处理；外部文件内容由 M4 附件服务统一清理。
 * 操作幂等记录没有实例外键，故意保留以支持删除请求重放。</p>
 *
 * @author FlowMind
 * @since 2026-07-23
 */
@Repository
public class ProcessInstanceDeletionRepository {

    private final JdbcTemplate jdbcTemplate;

    /** 创建实例删除仓储。 */
    public ProcessInstanceDeletionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 删除实例关联运行数据，并保留审计、回调与幂等记录。
     *
     * @param instanceId 流程实例 ID
     * @return 删除实例行数，正常为 1
     */
    public int deleteRuntimeData(String instanceId) {
        jdbcTemplate.update("DELETE FROM process_attachment WHERE instance_id = ?", instanceId);
        jdbcTemplate.update("DELETE FROM process_read_record WHERE instance_id = ?", instanceId);
        jdbcTemplate.update("DELETE FROM process_reminder_record WHERE instance_id = ?", instanceId);
        jdbcTemplate.update("DELETE FROM process_alert_record WHERE instance_id = ?", instanceId);
        jdbcTemplate.update("DELETE FROM process_history_task WHERE instance_id = ?", instanceId);
        jdbcTemplate.update("DELETE FROM process_active_task WHERE instance_id = ?", instanceId);
        jdbcTemplate.update("UPDATE process_task_group SET parent_group_id = NULL WHERE parent_group_id IN "
                + "(SELECT id FROM process_task_group WHERE instance_id = ?)", instanceId);
        jdbcTemplate.update("DELETE FROM process_task_group WHERE instance_id = ?", instanceId);
        jdbcTemplate.update("UPDATE process_audit_log SET instance_id = NULL, "
                + "detail_json = json_set(COALESCE(detail_json, '{}'), '$.targetDeleted', 1, "
                + "'$.deleteMode', 'HARD') WHERE instance_id = ?", instanceId);
        jdbcTemplate.update("UPDATE process_callback_log SET instance_id = NULL, "
                + "payload_json = json_set(COALESCE(payload_json, '{}'), '$.targetDeleted', 1, "
                + "'$.deleteMode', 'HARD') "
                + "WHERE instance_id = ?", instanceId);
        return jdbcTemplate.update("DELETE FROM process_instance WHERE id = ?", instanceId);
    }

    public List<String> findAttachmentStorageKeys(String instanceId) {
        return jdbcTemplate.queryForList("SELECT storage_key FROM process_attachment WHERE instance_id = ?",
                String.class, instanceId);
    }
}
