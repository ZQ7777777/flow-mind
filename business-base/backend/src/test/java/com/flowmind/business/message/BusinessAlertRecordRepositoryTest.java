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
        ProcessAlertRecordEntity alert = alert("TASK_TIMEOUT",
                "{\"dueAt\":\"2026-08-14T08:30\",\"action\":\"FORCE_COMPLETE\"}");

        assertEquals(1, repository.insert(alert));

        ArgumentCaptor<ProcessMessage> captor = ArgumentCaptor.forClass(ProcessMessage.class);
        verify(publisher).publish(captor.capture());
        ProcessMessage message = captor.getValue();
        assertEquals("alert-1", message.getMessageId());
        assertEquals("ALERT", message.getMessageType());
        assertEquals("您有一条告警异常急需处理，请关注。\n"
                + "告警类型：任务超时\n"
                + "告警原因：任务已超过处理期限，到期时间：2026-08-14T08:30，处理动作：强制办结",
                message.getContent());
        assertTrue(message.getTargetUserIds().isEmpty());
        assertEquals("alert-1", message.getPayload().get("alertId"));
        assertEquals("TASK_TIMEOUT", message.getPayload().get("alertType"));
    }

    @Test
    void actionExceptionNotificationUsesErrorSummaryAsReason() throws Exception {
        MessagePublisher publisher = mock(MessagePublisher.class);
        BusinessAlertRecordRepository repository = repository(publisher);

        repository.insert(alert("ACTION_EXCEPTION",
                "{\"errorCode\":\"INSTANCE_STATUS_INVALID\","
                        + "\"errorSummary\":\"流程实例状态不允许强制办结\"}"));

        ArgumentCaptor<ProcessMessage> captor = ArgumentCaptor.forClass(ProcessMessage.class);
        verify(publisher).publish(captor.capture());
        assertEquals("您有一条告警异常急需处理，请关注。\n"
                + "告警类型：动作异常\n"
                + "告警原因：流程实例状态不允许强制办结", captor.getValue().getContent());
    }

    @Test
    void callbackFailureNotificationUsesErrorSummaryAsReason() throws Exception {
        MessagePublisher publisher = mock(MessagePublisher.class);
        BusinessAlertRecordRepository repository = repository(publisher);

        repository.insert(alert("CALLBACK_FAILED", "{\"errorSummary\":\"回调服务连接超时\"}"));

        ArgumentCaptor<ProcessMessage> captor = ArgumentCaptor.forClass(ProcessMessage.class);
        verify(publisher).publish(captor.capture());
        assertEquals("您有一条告警异常急需处理，请关注。\n"
                + "告警类型：回调失败\n"
                + "告警原因：回调服务连接超时", captor.getValue().getContent());
    }

    @Test
    void malformedAlertDetailUsesSafeFallbackReason() throws Exception {
        MessagePublisher publisher = mock(MessagePublisher.class);
        BusinessAlertRecordRepository repository = repository(publisher);

        repository.insert(alert("ACTION_EXCEPTION", "not-json"));

        ArgumentCaptor<ProcessMessage> captor = ArgumentCaptor.forClass(ProcessMessage.class);
        verify(publisher).publish(captor.capture());
        assertEquals("您有一条告警异常急需处理，请关注。\n"
                + "告警类型：动作异常\n"
                + "告警原因：动作执行发生异常", captor.getValue().getContent());
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

        repository.insert(alert("TASK_TIMEOUT", "{}"));

        assertEquals(1L, messageRepository.countUnread("admin-1"));
        assertEquals(1L, messageRepository.countUnread("admin-2"));
        List<BusinessUserMessageEntity> messages = messageRepository.query("admin-1", "UNREAD", "ALERT", 10, 0);
        assertEquals(1, messages.size());
        assertEquals("您有一条告警异常急需处理，请关注。\n"
                + "告警类型：任务超时\n"
                + "告警原因：任务已超过处理期限", messages.get(0).getContent());
    }

    private BusinessAlertRecordRepository repository(MessagePublisher publisher) throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(
                DriverManager.getConnection("jdbc:sqlite::memory:"), true));
        jdbc.execute("CREATE TABLE process_alert_record ("
                + "id TEXT PRIMARY KEY, instance_id TEXT, task_id TEXT, alert_type TEXT, severity TEXT, "
                + "alert_status TEXT, detail_json TEXT, handled_by TEXT, handled_at TEXT, created_at TEXT)");
        return new BusinessAlertRecordRepository(jdbc, publisher);
    }

    private ProcessAlertRecordEntity alert(String alertType, String detailJson) {
        ProcessAlertRecordEntity alert = new ProcessAlertRecordEntity();
        alert.setId("alert-1");
        alert.setInstanceId("instance-1");
        alert.setTaskId("task-1");
        alert.setAlertType(alertType);
        alert.setSeverity("HIGH");
        alert.setAlertStatus("OPEN");
        alert.setDetailJson(detailJson);
        alert.setCreatedAt(LocalDateTime.of(2026, 8, 14, 9, 0));
        return alert;
    }
}
