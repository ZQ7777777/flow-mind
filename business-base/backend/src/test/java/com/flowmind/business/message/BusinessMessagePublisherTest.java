package com.flowmind.business.message;

import com.flowmind.platform.api.dto.ProcessMessage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessMessagePublisherTest {

    @Test
    void persistsOneInboxRowPerTargetAndKeepsPublishIdempotent() {
        JdbcTemplate jdbc = jdbc();
        BusinessUserMessageRepository repository = new BusinessUserMessageRepository(jdbc);
        BusinessMessagePublisher publisher = new BusinessMessagePublisher(repository, null, null);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("taskId", "task-1");
        ProcessMessage message = new ProcessMessage("source-1", "TASK_DUE_SOON", "任务即将超时",
                "请及时处理", Arrays.asList("u_sales_01", "u_finance_01"), payload,
                LocalDateTime.of(2026, 8, 12, 9, 0));

        publisher.publish(message);
        publisher.publish(message);

        assertEquals(2L, repository.countAll());
        assertEquals(1L, repository.countUnread("u_sales_01"));
        assertEquals(1L, repository.countUnread("u_finance_01"));
    }

    @Test
    void alertMessagesArePersistedForAdministrators() {
        JdbcTemplate jdbc = jdbc();
        BusinessUserMessageRepository repository = new BusinessUserMessageRepository(jdbc);
        BusinessMessagePublisher publisher = new BusinessMessagePublisher(repository,
                new StaticBusinessAdministratorProvider("u_admin_01", "u_admin_02"), null);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("alertId", "alert-1");
        payload.put("alertType", "ACTION_EXCEPTION");
        ProcessMessage message = new ProcessMessage("alert-1", "ALERT", "流程异常告警",
                "请及时处理", Collections.<String>emptyList(), payload,
                LocalDateTime.of(2026, 8, 12, 9, 0));

        publisher.publish(message);

        assertEquals(2L, repository.countAll());
        assertEquals(1L, repository.countUnread("u_admin_01"));
        assertEquals(1L, repository.countUnread("u_admin_02"));
    }

    private JdbcTemplate jdbc() {
        SingleConnectionDataSource dataSource;
        try {
            dataSource = new SingleConnectionDataSource(DriverManager.getConnection("jdbc:sqlite::memory:"), true);
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException(ex);
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        BusinessMessageSchemaInitializer.initialize(jdbc);
        return jdbc;
    }
}