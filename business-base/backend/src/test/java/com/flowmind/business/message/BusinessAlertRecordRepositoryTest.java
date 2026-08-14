package com.flowmind.business.message;

import com.flowmind.platform.api.dto.ProcessMessage;
import com.flowmind.platform.api.spi.MessagePublisher;
import com.flowmind.platform.persistence.entity.ProcessAlertRecordEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BusinessAlertRecordRepositoryTest {

    @Test
    void successfulAlertInsertPublishesFixedAdministratorNotification() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(
                DriverManager.getConnection("jdbc:sqlite::memory:"), true));
        jdbc.execute("CREATE TABLE process_alert_record ("
                + "id TEXT PRIMARY KEY, instance_id TEXT, task_id TEXT, alert_type TEXT, severity TEXT, "
                + "alert_status TEXT, detail_json TEXT, handled_by TEXT, handled_at TEXT, created_at TEXT)");
        MessagePublisher publisher = mock(MessagePublisher.class);
        BusinessAlertRecordRepository repository = new BusinessAlertRecordRepository(jdbc, publisher);
        ProcessAlertRecordEntity alert = alert();

        assertEquals(1, repository.insert(alert));

        ArgumentCaptor<ProcessMessage> captor = ArgumentCaptor.forClass(ProcessMessage.class);
        verify(publisher).publish(captor.capture());
        ProcessMessage message = captor.getValue();
        assertEquals("alert-1", message.getMessageId());
        assertEquals("ALERT", message.getMessageType());
        assertEquals("您有一条告警异常急需处理，请关注。", message.getContent());
        assertTrue(message.getTargetUserIds().isEmpty());
        assertEquals("alert-1", message.getPayload().get("alertId"));
        assertEquals("TASK_TIMEOUT", message.getPayload().get("alertType"));
    }

    @Test
    void alertNotificationIsStoredForEveryAdministrator() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(
                DriverManager.getConnection("jdbc:sqlite::memory:"), true));
        jdbc.execute("CREATE TABLE process_alert_record ("
                + "id TEXT PRIMARY KEY, instance_id TEXT, task_id TEXT, alert_type TEXT, severity TEXT, "
                + "alert_status TEXT, detail_json TEXT, handled_by TEXT, handled_at TEXT, created_at TEXT)");
        BusinessMessageSchemaInitializer.initialize(jdbc);
        BusinessUserMessageRepository messageRepository = new BusinessUserMessageRepository(jdbc);
        BusinessMessagePublisher publisher = new BusinessMessagePublisher(messageRepository,
                new StaticBusinessAdministratorProvider("admin-1", "admin-2"), null);
        BusinessAlertRecordRepository repository = new BusinessAlertRecordRepository(jdbc, publisher);

        repository.insert(alert());

        assertEquals(1L, messageRepository.countUnread("admin-1"));
        assertEquals(1L, messageRepository.countUnread("admin-2"));
        List<BusinessUserMessageEntity> messages = messageRepository.query("admin-1", "UNREAD", "ALERT", 10, 0);
        assertEquals(1, messages.size());
        assertEquals("您有一条告警异常急需处理，请关注。", messages.get(0).getContent());
    }

    private ProcessAlertRecordEntity alert() {
        ProcessAlertRecordEntity alert = new ProcessAlertRecordEntity();
        alert.setId("alert-1");
        alert.setInstanceId("instance-1");
        alert.setTaskId("task-1");
        alert.setAlertType("TASK_TIMEOUT");
        alert.setSeverity("HIGH");
        alert.setAlertStatus("OPEN");
        alert.setDetailJson("{}");
        alert.setCreatedAt(LocalDateTime.of(2026, 8, 14, 9, 0));
        return alert;
    }
}
