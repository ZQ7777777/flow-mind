package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 流程实例只读仓储。M2 阶段主要供运行期状态校验和查询组装复用。
 */
@Repository
public class ProcessInstanceRepository {

    private static final RowMapper<ProcessInstanceEntity> ROW_MAPPER =
            new RowMapper<ProcessInstanceEntity>() {
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

    private final JdbcTemplate jdbcTemplate;

    public ProcessInstanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 按实例 ID 读取实例快照；不存在时返回 null。 */
    public ProcessInstanceEntity findById(String id) {
        List<ProcessInstanceEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_instance WHERE id = ?",
                ROW_MAPPER, id);
        return results.isEmpty() ? null : results.get(0);
    }
}
