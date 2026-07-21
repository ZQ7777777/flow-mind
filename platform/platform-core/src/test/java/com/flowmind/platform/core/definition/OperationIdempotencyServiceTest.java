package com.flowmind.platform.core.definition;

import com.flowmind.platform.api.enums.DefinitionActionTypeEnum;
import com.flowmind.platform.api.enums.OperationStatusEnum;
import com.flowmind.platform.persistence.entity.ProcessOperationRecordEntity;
import com.flowmind.platform.persistence.repository.ProcessOperationRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationIdempotencyServiceTest {

    private static final String SCHEMA = "/schema/sqlite/001_init_flow_platform.sql";

    private Connection connection;
    private ProcessOperationRecordRepository repository;
    private OperationIdempotencyService service;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        executeSchema(connection);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        repository = new ProcessOperationRecordRepository(jdbcTemplate);
        service = new OperationIdempotencyService(repository);
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void beginCreatesProcessingRecordWithLeaseAndRetention() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 10, 0);

        service.begin("operation-001",
                DefinitionActionTypeEnum.CREATE.getOperationActionType(),
                "operator-001",
                "hash-001",
                now);

        ProcessOperationRecordEntity record = repository.findByOperationId("operation-001");
        assertNotNull(record.getId());
        assertEquals("operation-001", record.getOperationId());
        assertEquals(DefinitionActionTypeEnum.CREATE.getOperationActionType(), record.getActionType());
        assertEquals("operator-001", record.getOperatorId());
        assertEquals("hash-001", record.getRequestHash());
        assertEquals(OperationStatusEnum.PROCESSING.name(), record.getOperationStatus());
        assertEquals(now.plusMinutes(5), record.getProcessingExpiresAt());
        assertEquals(now.plusDays(1), record.getExpiresAt());
        assertEquals(now, record.getCreatedAt());
        assertEquals(now, record.getUpdatedAt());
    }

    @Test
    void replayReturnsSuccessfulRecordAndRejectsActionOrHashConflicts() {
        service.begin("operation-002",
                DefinitionActionTypeEnum.SAVE_GRAPH.getOperationActionType(),
                "operator-001",
                "hash-001",
                LocalDateTime.of(2026, 7, 17, 10, 0));
        service.markSuccess("operation-002", "{\"definitionId\":\"definition-001\"}");

        ProcessOperationRecordEntity replay = service.replay("operation-002",
                DefinitionActionTypeEnum.SAVE_GRAPH.getOperationActionType(),
                "hash-001");

        assertEquals(OperationStatusEnum.SUCCESS.name(), replay.getOperationStatus());
        assertEquals("{\"definitionId\":\"definition-001\"}", replay.getResultJson());
        assertThrows(IllegalArgumentException.class,
                () -> service.replay("operation-002",
                        DefinitionActionTypeEnum.COPY.getOperationActionType(),
                        "hash-001"));
        assertThrows(IllegalArgumentException.class,
                () -> service.replay("operation-002",
                        DefinitionActionTypeEnum.SAVE_GRAPH.getOperationActionType(),
                        "changed-hash"));
    }

    @Test
    void replayReturnsNullWhenOperationDoesNotExist() {
        ProcessOperationRecordEntity replay = service.replay("missing-operation",
                DefinitionActionTypeEnum.DELETE.getOperationActionType(),
                "hash-001");

        assertNull(replay);
    }

    @Test
    void beginOrReplayReturnsBaselineDecisionTypesForFailedConflictingAndProcessingRecords() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 10, 0);

        OperationIdempotencyDecision first = service.beginOrReplay("operation-decision",
                DefinitionActionTypeEnum.SAVE_GRAPH.getOperationActionType(),
                "operator-001",
                "hash-001",
                now);
        OperationIdempotencyDecision inProgress = service.beginOrReplay("operation-decision",
                DefinitionActionTypeEnum.SAVE_GRAPH.getOperationActionType(),
                "operator-001",
                "hash-001",
                now.plusMinutes(1));
        OperationIdempotencyDecision conflict = service.beginOrReplay("operation-decision",
                DefinitionActionTypeEnum.SAVE_GRAPH.getOperationActionType(),
                "operator-001",
                "changed-hash",
                now.plusMinutes(1));
        OperationIdempotencyDecision takeover = service.beginOrReplay("operation-decision",
                DefinitionActionTypeEnum.SAVE_GRAPH.getOperationActionType(),
                "operator-001",
                "hash-001",
                now.plusMinutes(6));
        service.markFailed("operation-decision", "FLOW_DEFINITION_INVALID");
        OperationIdempotencyDecision failed = service.beginOrReplay("operation-decision",
                DefinitionActionTypeEnum.SAVE_GRAPH.getOperationActionType(),
                "operator-001",
                "hash-001",
                now.plusMinutes(7));

        assertEquals(OperationIdempotencyDecisionType.NEW, first.getType());
        assertEquals(OperationIdempotencyDecisionType.IN_PROGRESS, inProgress.getType());
        assertEquals(OperationIdempotencyDecisionType.CONFLICT, conflict.getType());
        assertEquals(OperationIdempotencyDecisionType.TAKE_OVER, takeover.getType());
        assertEquals(now.plusMinutes(11), takeover.getRecord().getProcessingExpiresAt());
        assertEquals(OperationIdempotencyDecisionType.REPLAY_FAILED, failed.getType());
        assertEquals("FLOW_DEFINITION_INVALID", failed.getRecord().getErrorCode());
    }

    @Test
    void markFailedStoresFailedStatusAndErrorCode() {
        service.begin("operation-003",
                DefinitionActionTypeEnum.DELETE.getOperationActionType(),
                "operator-001",
                "hash-001",
                LocalDateTime.of(2026, 7, 17, 10, 0));

        service.markFailed("operation-003", "FLOW_TASK_CONCURRENT_MODIFIED");

        ProcessOperationRecordEntity record = repository.findByOperationId("operation-003");
        assertEquals(OperationStatusEnum.FAILED.name(), record.getOperationStatus());
        assertNull(record.getResultJson());
        assertEquals("FLOW_TASK_CONCURRENT_MODIFIED", record.getErrorCode());
        assertThrows(IllegalStateException.class,
                () -> service.replay("operation-003",
                        DefinitionActionTypeEnum.DELETE.getOperationActionType(),
                        "hash-001"));
    }

    private static void executeSchema(Connection connection) throws IOException, SQLException {
        String sql;
        try (InputStream input = OperationIdempotencyServiceTest.class.getResourceAsStream(SCHEMA)) {
            assertNotNull(input);
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
