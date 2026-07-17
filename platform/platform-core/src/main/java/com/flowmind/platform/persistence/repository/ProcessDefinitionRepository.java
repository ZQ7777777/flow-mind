package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.api.dto.ProcessDefinitionQuery;
import com.flowmind.platform.persistence.entity.ProcessDefinitionEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
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

    public ProcessDefinitionEntity findById(String id) {
        List<ProcessDefinitionEntity> results = jdbcTemplate.query(
                "SELECT * FROM process_definition WHERE id = ?",
                DefinitionRowMappers.DEFINITION, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public Integer findMaxVersionByProcessCode(String processCode) {
        return jdbcTemplate.queryForObject(
                "SELECT MAX(version) FROM process_definition WHERE process_code = ?",
                Integer.class, processCode);
    }

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

    public int touchUpdated(String id, String updatedBy) {
        return jdbcTemplate.update("UPDATE process_definition "
                + "SET updated_by = ?, updated_at = datetime('now') WHERE id = ?", updatedBy, id);
    }

    public int deleteById(String id) {
        return jdbcTemplate.update("DELETE FROM process_definition WHERE id = ?", id);
    }

    public long countByQuery(ProcessDefinitionQuery query) {
        QueryParts parts = buildQueryParts(query);
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_definition" + parts.whereClause,
                parts.args.toArray(), Long.class);
        return count == null ? 0L : count.longValue();
    }

    public List<ProcessDefinitionEntity> searchByQuery(ProcessDefinitionQuery query) {
        QueryParts parts = buildQueryParts(query);
        int pageNo = pageNo(query);
        int pageSize = pageSize(query);
        List<Object> args = new ArrayList<Object>(parts.args);
        args.add(Integer.valueOf(pageSize));
        args.add(Integer.valueOf((pageNo - 1) * pageSize));
        return jdbcTemplate.query("SELECT * FROM process_definition" + parts.whereClause
                        + " ORDER BY updated_at DESC, process_code ASC, version DESC LIMIT ? OFFSET ?",
                DefinitionRowMappers.DEFINITION, args.toArray());
    }

    public boolean existsInstanceByDefinitionId(String definitionId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM process_instance WHERE definition_id = ?",
                Long.class, definitionId);
        return count != null && count.longValue() > 0L;
    }

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

    private int pageNo(ProcessDefinitionQuery query) {
        if (query == null || query.getPageNo() == null || query.getPageNo().intValue() < 1) {
            return DEFAULT_PAGE_NO;
        }
        return query.getPageNo().intValue();
    }

    private int pageSize(ProcessDefinitionQuery query) {
        if (query == null || query.getPageSize() == null || query.getPageSize().intValue() < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return query.getPageSize().intValue();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class QueryParts {
        private final List<String> conditions = new ArrayList<String>();
        private final List<Object> args = new ArrayList<Object>();
        private String whereClause = "";

        private void add(String condition, Object value) {
            conditions.add(condition);
            args.add(value);
            whereClause = " WHERE " + joinConditions();
        }

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
