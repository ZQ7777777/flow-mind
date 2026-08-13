package com.flowmind.business.message;

import com.flowmind.business.security.CurrentBusinessUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessMessageServiceTest {

    @Test
    void queriesAndMarksOnlyCurrentUserMessages() {
        JdbcTemplate jdbc = jdbc();
        BusinessUserMessageRepository repository = new BusinessUserMessageRepository(jdbc);
        BusinessMessageService service = new BusinessMessageService(repository,
                () -> new CurrentBusinessUserProvider.BusinessUser("u_sales_01", "dept_sales"));
        insert(repository, "message-1", "source-1", "u_sales_01");
        insert(repository, "message-2", "source-2", "u_finance_01");

        BusinessMessagePageResponse page = service.query(new BusinessMessageQuery());
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getUnreadCount()).isEqualTo(1L);

        service.markRead("message-1");

        assertThat(service.unreadCount().getUnreadCount()).isEqualTo(0L);
        assertThat(repository.countUnread("u_finance_01")).isEqualTo(1L);
    }

    private void insert(BusinessUserMessageRepository repository, String id, String sourceId, String recipient) {
        BusinessUserMessageEntity entity = new BusinessUserMessageEntity();
        entity.setId(id);
        entity.setSourceMessageId(sourceId);
        entity.setRecipientUserId(recipient);
        entity.setMessageType("TASK_DUE_SOON");
        entity.setTitle("任务即将超时");
        entity.setContent("请及时处理");
        entity.setSeverity("NORMAL");
        entity.setPayloadJson("{}");
        entity.setReadStatus("UNREAD");
        entity.setCreatedAt(LocalDateTime.of(2026, 8, 12, 9, 0));
        repository.insertIgnore(entity);
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