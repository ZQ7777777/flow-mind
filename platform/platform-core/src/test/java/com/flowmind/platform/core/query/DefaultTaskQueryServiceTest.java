package com.flowmind.platform.core.query;

import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.ProcessCommentDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.core.task.HistoryArchiveCommand;
import com.flowmind.platform.core.task.HistoryTaskWriter;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import com.flowmind.platform.testsupport.ExistingConnectionDataSource;
import com.flowmind.platform.testsupport.SchemaTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultTaskQueryServiceTest {

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private ProcessHistoryTaskRepository historyTaskRepository;
    private HistoryTaskWriter historyTaskWriter;
    private DefaultTaskQueryService taskQueryService;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaTestSupport.executeSchema(connection);
        jdbcTemplate = new JdbcTemplate(new ExistingConnectionDataSource(connection));
        historyTaskRepository = new ProcessHistoryTaskRepository(jdbcTemplate);
        historyTaskWriter = new HistoryTaskWriter(historyTaskRepository);
        taskQueryService = new DefaultTaskQueryService(historyTaskRepository, new ProcessTraceAssembler());
        insertDefinitionAndInstance();
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void historyWriterArchivesTaskAndQueryServiceReturnsTraceAndComments() {
        ProcessHistoryTaskEntity first = historyTaskWriter.archive(command(
                "task-apply", "apply", "op-apply", "提交申请", LocalDateTime.of(2026, 7, 22, 9, 0)));
        ProcessHistoryTaskEntity second = historyTaskWriter.archive(command(
                "task-review", "review", "op-review", "同意", LocalDateTime.of(2026, 7, 22, 10, 0)));
        historyTaskWriter.archive(command(
                "task-finance", "finance", "op-finance", " ", LocalDateTime.of(2026, 7, 22, 11, 0)));

        List<HistoryTaskDTO> history = taskQueryService.queryHistoryTasks("instance-1");
        List<ProcessCommentDTO> comments = taskQueryService.queryComments("instance-1");

        assertEquals(3, history.size());
        assertEquals(first.getId(), history.get(0).getHistoryTaskId());
        assertEquals(second.getId(), history.get(1).getHistoryTaskId());
        assertEquals("提交申请", history.get(0).getComment());
        assertEquals("1000", String.valueOf(history.get(0).getVariablesSnapshot().get("amount")));
        assertEquals(2, comments.size());
        assertEquals(first.getId(), comments.get(0).getCommentId());
        assertEquals("task-apply", comments.get(0).getTaskId());
        assertEquals("同意", comments.get(1).getComment());
    }

    @Test
    void historyWriterReturnsExistingRecordForSameTaskActionOperation() {
        HistoryArchiveCommand command = command("task-apply", "apply", "op-apply",
                "提交申请", LocalDateTime.of(2026, 7, 22, 9, 0));

        ProcessHistoryTaskEntity first = historyTaskWriter.archive(command);
        ProcessHistoryTaskEntity replay = historyTaskWriter.archive(command);

        assertEquals(first.getId(), replay.getId());
        assertEquals(1, taskQueryService.queryHistoryTasks("instance-1").size());
    }

    private HistoryArchiveCommand command(String taskId,
                                          String nodeCode,
                                          String operationId,
                                          String comment,
                                          LocalDateTime completedAt) {
        HistoryArchiveCommand command = new HistoryArchiveCommand();
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setId("instance-1");
        ProcessActiveTaskEntity task = new ProcessActiveTaskEntity();
        task.setId(taskId);
        task.setInstanceId("instance-1");
        task.setNodeCode(nodeCode);
        task.setTaskGroupId("group-1");
        task.setBranchKey("main");
        task.setCreatedAt(completedAt.minusMinutes(30));
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("amount", Integer.valueOf(1000));
        command.setInstance(instance);
        command.setTask(task);
        command.setOperator(new UserContext("operator-001", "Operator", "dept-001", "Dept"));
        command.setActionType(ActionTypeEnum.APPROVE);
        command.setOperationId(operationId);
        command.setComment(comment);
        command.setVariablesSnapshot(variables);
        command.setCompletedAt(completedAt);
        return command;
    }

    private void insertDefinitionAndInstance() {
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "definition-1", "history-test", "History Test", "test", 1, "test");
        jdbcTemplate.update("INSERT INTO process_instance "
                        + "(id, definition_id, process_code, process_name, version, instance_title, "
                        + "starter_user_id, starter_user_name, instance_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "instance-1", "definition-1", "history-test", "History Test", 1,
                "History Test Instance", "starter", "Starter", "RUNNING");
    }
}
