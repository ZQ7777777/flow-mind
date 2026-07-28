package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.api.dto.AlertQuery;
import com.flowmind.platform.api.dto.AuditLogQuery;
import com.flowmind.platform.api.dto.ReadRecordQuery;
import com.flowmind.platform.api.dto.ReminderQuery;
import com.flowmind.platform.api.enums.AlertSeverityEnum;
import com.flowmind.platform.api.enums.AlertStatusEnum;
import com.flowmind.platform.api.enums.AlertTypeEnum;
import com.flowmind.platform.api.enums.OperationTargetTypeEnum;
import com.flowmind.platform.api.enums.ReminderStatusEnum;
import com.flowmind.platform.api.enums.ReminderTypeEnum;
import com.flowmind.platform.persistence.entity.ProcessAlertRecordEntity;
import com.flowmind.platform.persistence.entity.ProcessAuditLogEntity;
import com.flowmind.platform.persistence.entity.ProcessReadRecordEntity;
import com.flowmind.platform.persistence.entity.ProcessReminderRecordEntity;
import com.flowmind.platform.testsupport.ExistingConnectionDataSource;
import com.flowmind.platform.testsupport.SchemaTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class M5MonitorRepositoryIntegrationTest {

    private Connection connection;
    private ProcessReadRecordRepository readRepository;
    private ProcessAuditLogRepository auditRepository;
    private ReminderRecordRepository reminderRepository;
    private AlertRecordRepository alertRepository;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaTestSupport.executeSchema(connection);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new ExistingConnectionDataSource(connection));
        seedProcessData(jdbcTemplate);
        readRepository = new ProcessReadRecordRepository(jdbcTemplate);
        auditRepository = new ProcessAuditLogRepository(jdbcTemplate);
        reminderRepository = new ReminderRecordRepository(jdbcTemplate);
        alertRepository = new AlertRecordRepository(jdbcTemplate);
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }

    private void seedProcessData(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "definition-1", "leave", "Leave", "hr", 1, "tester");
        jdbcTemplate.update("INSERT INTO process_instance "
                        + "(id, definition_id, process_code, process_name, version, instance_title, "
                        + "starter_user_id, starter_user_name, instance_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "instance-1", "definition-1", "leave", "Leave", 1, "Leave request",
                "starter-1", "Starter One", "RUNNING");
        jdbcTemplate.update("INSERT INTO process_active_task "
                        + "(id, instance_id, definition_id, node_code, candidate_user_ids, task_status, lock_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "task-1", "instance-1", "definition-1", "approve", "[\"user-1\"]", "ACTIVE", 0);
    }

    @Test
    void readRecordUpsertKeepsOneRowAndRefreshesReadTime() {
        LocalDateTime firstRead = LocalDateTime.of(2026, 7, 28, 9, 0);
        ProcessReadRecordEntity first = readRepository.upsert("instance-1", "user-1", "User One", firstRead);
        ProcessReadRecordEntity second = readRepository.upsert("instance-1", "user-1", "User One",
                firstRead.plusMinutes(1));

        ReadRecordQuery query = new ReadRecordQuery();
        query.setInstanceId("instance-1");
        List<ProcessReadRecordEntity> records = readRepository.query(query);
        assertEquals(1, records.size());
        assertEquals(first.getId(), second.getId());
        assertNotEquals(firstRead, records.get(0).getReadAt());
    }

    @Test
    void auditReminderAndAlertRepositoriesSupportM5StateTransitions() {
        ProcessAuditLogEntity audit = new ProcessAuditLogEntity();
        audit.setId("audit-1");
        audit.setOperationId("op-audit-1");
        audit.setTargetType(OperationTargetTypeEnum.TASK.name());
        audit.setTargetId("task-1");
        audit.setActionType("CLAIM");
        audit.setOperatorId("user-1");
        audit.setCreatedAt(LocalDateTime.of(2026, 7, 28, 9, 0));
        assertEquals(1, auditRepository.insert(audit));

        AuditLogQuery auditQuery = new AuditLogQuery();
        auditQuery.setTargetType(OperationTargetTypeEnum.TASK);
        assertEquals(1L, auditRepository.count(auditQuery));

        ProcessReminderRecordEntity reminder = new ProcessReminderRecordEntity();
        reminder.setId("reminder-1");
        reminder.setInstanceId("instance-1");
        reminder.setTaskId("task-1");
        reminder.setReminderType("MANUAL");
        reminder.setTargetUserIds("[\"user-1\"]");
        reminder.setMessage("Please process");
        reminder.setReminderStatus("PENDING");
        reminder.setCreatedBy("user-2");
        assertEquals(1, reminderRepository.insert(reminder));
        assertEquals(1, reminderRepository.markSent(reminder.getId()));

        ReminderQuery reminderQuery = new ReminderQuery();
        reminderQuery.setTaskId("task-1");
        reminderQuery.setReminderType(ReminderTypeEnum.MANUAL);
        reminderQuery.setReminderStatus(ReminderStatusEnum.SENT);
        assertEquals("SENT", reminderRepository.query(reminderQuery).get(0).getReminderStatus());

        ProcessAlertRecordEntity alert = new ProcessAlertRecordEntity();
        alert.setId("alert-1");
        alert.setInstanceId("instance-1");
        alert.setTaskId("task-1");
        alert.setAlertType("TASK_TIMEOUT");
        alert.setSeverity("MEDIUM");
        alert.setAlertStatus("OPEN");
        alert.setDetailJson("{}");
        assertEquals(1, alertRepository.insert(alert));
        assertEquals(1, alertRepository.handle(alert.getId(), "HANDLED", "admin-1", LocalDateTime.now()));
        assertEquals(0, alertRepository.handle(alert.getId(), "IGNORED", "admin-1", LocalDateTime.now()));

        AlertQuery alertQuery = new AlertQuery();
        alertQuery.setTaskId("task-1");
        alertQuery.setAlertType(AlertTypeEnum.TASK_TIMEOUT);
        alertQuery.setSeverity(AlertSeverityEnum.MEDIUM);
        alertQuery.setAlertStatus(AlertStatusEnum.HANDLED);
        assertEquals(1L, alertRepository.count(alertQuery));
    }
}
