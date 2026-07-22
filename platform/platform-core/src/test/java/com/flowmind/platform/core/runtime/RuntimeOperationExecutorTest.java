package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.request.TaskOperationRequest;
import com.flowmind.platform.core.definition.OperationIdempotencyDecision;
import com.flowmind.platform.core.definition.OperationIdempotencyDecisionType;
import com.flowmind.platform.core.definition.OperationIdempotencyService;
import com.flowmind.platform.persistence.entity.ProcessOperationRecordEntity;
import com.flowmind.platform.persistence.repository.ProcessOperationRecordRepository;
import com.flowmind.platform.testsupport.ExistingConnectionDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeOperationExecutorTest {

    private static final String SCHEMA = "/schema/sqlite/001_init_flow_platform.sql";

    private Connection connection;
    private ProcessOperationRecordRepository repository;
    private RuntimeOperationExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        executeSchema(connection);
        repository = new ProcessOperationRecordRepository(new JdbcTemplate(new ExistingConnectionDataSource(connection)));
        executor = new RuntimeOperationExecutor(new OperationIdempotencyService(repository));
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void successfulTaskOperationReplaysDecodedResultAndPreservesTargetBinding() {
        TaskOperationRequest request = request("operation-approve", "approved");
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 10, 0);

        OperationIdempotencyDecision first = executor.begin(request, "APPROVE", "user-1",
                "instance-1", "task-1", now);
        executor.assertExecutable(first);
        TaskActionResult firstResult = new TaskActionResult();
        firstResult.setOperationId("operation-approve");
        executor.markSuccess("operation-approve", firstResult);

        OperationIdempotencyDecision replay = executor.begin(request, "APPROVE", "user-1",
                "instance-1", "task-1", now.plusMinutes(1));
        TaskActionResult replayed = executor.replayTaskAction(replay);
        ProcessOperationRecordEntity record = repository.findByOperationId("operation-approve");

        assertEquals(OperationIdempotencyDecisionType.NEW, first.getType());
        assertEquals(OperationIdempotencyDecisionType.REPLAY_SUCCESS, replay.getType());
        assertEquals("operation-approve", replayed.getOperationId());
        assertEquals(true, replayed.isReplayed());
        assertEquals("instance-1", record.getInstanceId());
        assertEquals("task-1", record.getTaskId());
    }

    @Test
    void changedRequestAndProcessingLeaseReturnFrozenErrors() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 10, 0);
        TaskOperationRequest original = request("operation-approve", "approved");
        executor.begin(original, "APPROVE", "user-1", "instance-1", "task-1", now);

        TaskOperationRequest changed = request("operation-approve", "changed comment");
        OperationIdempotencyDecision conflict = executor.begin(changed, "APPROVE", "user-1",
                "instance-1", "task-1", now.plusSeconds(1));
        RuntimeValidationException conflictError = assertThrows(RuntimeValidationException.class,
                () -> executor.assertExecutable(conflict));
        assertEquals(RuntimeErrorCodes.OPERATION_ID_CONFLICT, conflictError.getErrorCode());

        OperationIdempotencyDecision processing = executor.begin(original, "APPROVE", "user-1",
                "instance-1", "task-1", now.plusSeconds(1));
        RuntimeStateException processingError = assertThrows(RuntimeStateException.class,
                () -> executor.assertExecutable(processing));
        assertEquals(RuntimeErrorCodes.OPERATION_IN_PROGRESS, processingError.getErrorCode());
    }

    private TaskOperationRequest request(String operationId, String comment) {
        TaskOperationRequest request = new TaskOperationRequest();
        request.setOperationId(operationId);
        request.setTaskId("task-1");
        request.setExpectedTaskVersion(Long.valueOf(1));
        request.setOperatorUserId("user-1");
        request.setComment(comment);
        return request;
    }

    private static void executeSchema(Connection connection) throws IOException, SQLException {
        String sql;
        try (InputStream input = RuntimeOperationExecutorTest.class.getResourceAsStream(SCHEMA)) {
            if (input == null) {
                throw new IOException("Schema resource not found: " + SCHEMA);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            sql = new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
        try (Statement statement = connection.createStatement()) {
            for (String command : sql.split(";")) {
                if (!command.trim().isEmpty()) {
                    statement.execute(command);
                }
            }
        }
    }
}
