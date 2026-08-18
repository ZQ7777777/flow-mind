package com.flowmind.business.entry;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 初始化业务大厅入口配置表。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@Component
@DependsOn("platformSchemaInitializer")
public class BusinessEntryConfigSchemaInitializer implements InitializingBean {

    private final JdbcTemplate jdbcTemplate;

    public BusinessEntryConfigSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        initialize(jdbcTemplate);
    }

    public static void initialize(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS business_entry_config ("
                + "id TEXT PRIMARY KEY, "
                + "definition_id TEXT NOT NULL UNIQUE, "
                + "entry_display_name TEXT, "
                + "entry_page_url TEXT NOT NULL, "
                + "entry_source TEXT NOT NULL CHECK (entry_source IN ('MANUAL', 'AGENT_GENERATED')), "
                + "enabled INTEGER NOT NULL DEFAULT 0 CHECK (enabled IN (0, 1)), "
                + "remark TEXT, "
                + "generation_id TEXT, "
                + "artifact_revision TEXT, "
                + "created_by TEXT NOT NULL, "
                + "created_at TEXT NOT NULL DEFAULT (datetime('now')), "
                + "updated_by TEXT NOT NULL, "
                + "updated_at TEXT NOT NULL DEFAULT (datetime('now')), "
                + "FOREIGN KEY (definition_id) REFERENCES process_definition (id) ON DELETE CASCADE) ");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_business_entry_config_enabled "
                + "ON business_entry_config (enabled, updated_at)");
    }
}
