package com.flowmind.business.message;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Initializes Business Base user message persistence. */
@Component
public class BusinessMessageSchemaInitializer implements InitializingBean {

    private final JdbcTemplate jdbcTemplate;

    public BusinessMessageSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        initialize(jdbcTemplate);
    }

    public static void initialize(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS business_user_message ("
                + "id TEXT PRIMARY KEY, "
                + "source_message_id TEXT NOT NULL, "
                + "recipient_user_id TEXT NOT NULL, "
                + "message_type TEXT NOT NULL, "
                + "title TEXT NOT NULL, "
                + "content TEXT NOT NULL, "
                + "severity TEXT NOT NULL, "
                + "payload_json TEXT NOT NULL, "
                + "read_status TEXT NOT NULL DEFAULT 'UNREAD' CHECK (read_status IN ('UNREAD', 'READ')), "
                + "created_at TEXT NOT NULL DEFAULT (datetime('now')), "
                + "read_at TEXT, "
                + "UNIQUE (source_message_id, recipient_user_id))");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_business_user_message_recipient_read "
                + "ON business_user_message (recipient_user_id, read_status, created_at)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_business_user_message_recipient_created "
                + "ON business_user_message (recipient_user_id, created_at)");
    }
}