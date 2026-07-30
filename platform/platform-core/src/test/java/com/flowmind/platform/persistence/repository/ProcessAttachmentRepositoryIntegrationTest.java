package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.api.dto.AttachmentQuery;
import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import com.flowmind.platform.persistence.entity.ProcessAttachmentEntity;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessAttachmentRepositoryIntegrationTest {

    private static final String SCHEMA = "/schema/sqlite/001_init_flow_platform.sql";

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private ProcessAttachmentRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        executeSchema(connection);
        jdbcTemplate = new JdbcTemplate(new ExistingConnectionDataSource(connection));
        repository = new ProcessAttachmentRepository(jdbcTemplate);
        insertDefinitionAndInstances();
        insertTask("task-active", "instance-1", "ACTIVE", 3L);
        insertTask("task-claimed", "instance-1", "CLAIMED", 4L);
        insertTask("task-completed", "instance-1", "COMPLETED", 5L);
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void queryActiveRequiresInstanceAndAppliesFiltersWithStableOrdering() {
        insertAttachment("attachment-later", "instance-1", "task-active", "INSTANCE", "receipt", "bank",
                "storage-later", false, LocalDateTime.of(2026, 7, 27, 10, 1));
        insertAttachment("attachment-earlier", "instance-1", "task-active", "INSTANCE", "receipt", "bank",
                "storage-earlier", false, LocalDateTime.of(2026, 7, 27, 10, 0));
        insertAttachment("attachment-task", "instance-1", "task-active", "TASK", "receipt", "bank",
                "storage-task", false, LocalDateTime.of(2026, 7, 27, 10, 2));
        insertAttachment("attachment-deleted", "instance-1", "task-active", "INSTANCE", "receipt", "bank",
                "storage-deleted", true, LocalDateTime.of(2026, 7, 27, 9, 0));
        insertAttachment("attachment-other-field", "instance-1", "task-active", "INSTANCE", "receipt", "other",
                "storage-other-field", false, LocalDateTime.of(2026, 7, 27, 8, 0));
        insertAttachment("attachment-other-instance", "instance-2", "task-other", "INSTANCE", "receipt", "bank",
                "storage-other-instance", false, LocalDateTime.of(2026, 7, 27, 7, 0));

        AttachmentQuery query = new AttachmentQuery();
        query.setInstanceId("instance-1");
        query.setOwnerType(AttachmentOwnerTypeEnum.INSTANCE);
        query.setAttachmentCode("receipt");
        query.setFieldCode("bank");

        List<ProcessAttachmentEntity> attachments = repository.queryActive(query);

        assertEquals(2, attachments.size());
        assertEquals("attachment-earlier", attachments.get(0).getId());
        assertEquals("attachment-later", attachments.get(1).getId());
        assertThrows(IllegalArgumentException.class, () -> repository.queryActive(new AttachmentQuery()));
    }

    @Test
    void countActiveByInstanceAndCodeIncludesInstanceAndTaskAttachments() {
        insertAttachment("attachment-instance", "instance-1", "task-active", "INSTANCE", "receipt", null,
                "storage-instance", false, LocalDateTime.of(2026, 7, 27, 10, 0));
        insertAttachment("attachment-task", "instance-1", "task-active", "TASK", "receipt", null,
                "storage-task", false, LocalDateTime.of(2026, 7, 27, 10, 1));
        insertAttachment("attachment-deleted", "instance-1", "task-active", "TASK", "receipt", null,
                "storage-deleted", true, LocalDateTime.of(2026, 7, 27, 10, 2));
        insertAttachment("attachment-other-code", "instance-1", "task-active", "TASK", "contract", null,
                "storage-other", false, LocalDateTime.of(2026, 7, 27, 10, 3));

        assertEquals(2L, repository.countActiveByInstanceAndCode("instance-1", "receipt"));
    }

    @Test
    void insertWhenTaskOpenAndWithinLimitChecksTaskStateVersionAndCountAtomically() {
        ProcessAttachmentEntity first = attachmentEntity("attachment-first", "instance-1", "task-active", "INSTANCE",
                "receipt", "storage-first", LocalDateTime.of(2026, 7, 27, 10, 0));
        ProcessAttachmentEntity staleVersion = attachmentEntity("attachment-stale", "instance-1", "task-active", "INSTANCE",
                "receipt", "storage-stale", LocalDateTime.of(2026, 7, 27, 10, 1));
        ProcessAttachmentEntity closedTask = attachmentEntity("attachment-closed", "instance-1", "task-completed", "INSTANCE",
                "receipt", "storage-closed", LocalDateTime.of(2026, 7, 27, 10, 2));
        ProcessAttachmentEntity overLimit = attachmentEntity("attachment-over-limit", "instance-1", "task-active", "TASK",
                "receipt", "storage-over-limit", LocalDateTime.of(2026, 7, 27, 10, 3));

        assertEquals(1, repository.insertWhenTaskOpenAndWithinLimit(first, Long.valueOf(3), Integer.valueOf(1)));
        assertEquals(0, repository.insertWhenTaskOpenAndWithinLimit(staleVersion, Long.valueOf(2), Integer.valueOf(2)));
        assertEquals(0, repository.insertWhenTaskOpenAndWithinLimit(closedTask, Long.valueOf(5), Integer.valueOf(2)));
        assertEquals(0, repository.insertWhenTaskOpenAndWithinLimit(overLimit, Long.valueOf(3), Integer.valueOf(1)));
        assertEquals(1L, repository.countActiveByInstanceAndCode("instance-1", "receipt"));
    }

    @Test
    void softDeleteWhenTaskOpenKeepsMetadataAndRejectsClosedSourceTask() {
        insertAttachment("attachment-active", "instance-1", "task-active", "INSTANCE", "receipt", null,
                "storage-active", false, LocalDateTime.of(2026, 7, 27, 10, 0));
        insertAttachment("attachment-closed", "instance-1", "task-completed", "INSTANCE", "receipt", null,
                "storage-closed", false, LocalDateTime.of(2026, 7, 27, 10, 1));

        assertEquals(1, repository.softDeleteWhenTaskOpen("attachment-active", "task-active", "instance-1",
                "deleter", LocalDateTime.of(2026, 7, 27, 11, 0)));
        assertEquals(0, repository.softDeleteWhenTaskOpen("attachment-closed", "task-completed", "instance-1",
                "deleter", LocalDateTime.of(2026, 7, 27, 11, 0)));

        ProcessAttachmentEntity deleted = repository.findById("attachment-active");
        assertTrue(Boolean.TRUE.equals(deleted.getDeleted()));
        assertEquals("deleter", deleted.getDeletedBy());
        assertEquals("storage-active", deleted.getStorageKey());
    }

    @Test
    void replacementUsesCurrentTaskVersionAndPreservesClosedOriginalMetadata() {
        insertAttachment("attachment-old", "instance-1", "task-completed", "INSTANCE", "receipt", null,
                "storage-old", false, LocalDateTime.of(2026, 7, 27, 10, 0));

        assertEquals(0, repository.softDeleteForReplacement("attachment-old", "task-active", "instance-1",
                Long.valueOf(2), "starter", LocalDateTime.of(2026, 7, 27, 11, 0)));
        assertEquals(1, repository.softDeleteForReplacement("attachment-old", "task-active", "instance-1",
                Long.valueOf(3), "starter", LocalDateTime.of(2026, 7, 27, 11, 0)));

        ProcessAttachmentEntity old = repository.findById("attachment-old");
        assertTrue(Boolean.TRUE.equals(old.getDeleted()));
        assertEquals("task-completed", old.getTaskId());
        assertEquals("starter", old.getDeletedBy());
        ProcessAttachmentEntity replacement = attachmentEntity("attachment-new", "instance-1", "task-active",
                "INSTANCE", "receipt", "storage-new", LocalDateTime.of(2026, 7, 27, 11, 0));
        assertEquals(1, repository.insertWhenTaskOpenAndWithinLimit(replacement, Long.valueOf(3),
                Integer.valueOf(1)));
        assertEquals(1L, repository.countActiveByInstanceAndCode("instance-1", "receipt"));
    }

    @Test
    void storageKeyQueriesSupportInstanceBatchAndDefinitionCleanup() {
        insertAttachment("attachment-2", "instance-1", "task-active", "INSTANCE", "receipt", null,
                "storage-2", false, LocalDateTime.of(2026, 7, 27, 10, 1));
        insertAttachment("attachment-1", "instance-1", "task-active", "INSTANCE", "receipt", null,
                "storage-1", false, LocalDateTime.of(2026, 7, 27, 10, 0));
        insertAttachment("attachment-3", "instance-2", "task-other", "TASK", "receipt", null,
                "storage-3", false, LocalDateTime.of(2026, 7, 27, 10, 2));

        assertIterableEquals(Arrays.asList("storage-1", "storage-2"),
                repository.findStorageKeysByInstanceId("instance-1"));
        assertIterableEquals(Arrays.asList("storage-1", "storage-2", "storage-3"),
                repository.findStorageKeysByInstanceIds(Arrays.asList("instance-2", "instance-1")));
        assertIterableEquals(Arrays.asList("storage-1", "storage-2", "storage-3"),
                repository.findStorageKeysByDefinitionId("definition-1"));
        assertEquals(Collections.emptyList(), repository.findStorageKeysByInstanceIds(Collections.<String>emptyList()));
    }

    private void insertDefinitionAndInstances() {
        jdbcTemplate.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "definition-1", "attachment-test", "Attachment Test", "test", Integer.valueOf(1), "tester");
        insertInstance("instance-1", "definition-1");
        insertInstance("instance-2", "definition-1");
    }

    private void insertInstance(String id, String definitionId) {
        jdbcTemplate.update("INSERT INTO process_instance "
                        + "(id, definition_id, process_code, process_name, version, instance_title, "
                        + "starter_user_id, starter_user_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, definitionId, "attachment-test", "Attachment Test", Integer.valueOf(1),
                "Attachment Test Instance", "starter", "Starter");
    }

    private void insertTask(String id, String instanceId, String status, Long version) {
        jdbcTemplate.update("INSERT INTO process_active_task "
                        + "(id, instance_id, definition_id, node_code, task_status, lock_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, instanceId, "definition-1", "apply", status, version);
    }

    private void insertAttachment(String id, String instanceId, String taskId, String ownerType, String code,
                                  String fieldCode, String storageKey, boolean deleted, LocalDateTime uploadedAt) {
        ProcessAttachmentEntity entity = attachmentEntity(id, instanceId, taskId, ownerType, code, storageKey, uploadedAt);
        entity.setFieldCode(fieldCode);
        repository.insert(entity);
        if (deleted) {
            repository.softDelete(id, "deleter", uploadedAt.plusMinutes(1));
        }
    }

    private ProcessAttachmentEntity attachmentEntity(String id, String instanceId, String taskId, String ownerType,
                                                     String code, String storageKey, LocalDateTime uploadedAt) {
        ProcessAttachmentEntity entity = new ProcessAttachmentEntity();
        entity.setId(id);
        entity.setInstanceId(instanceId);
        entity.setTaskId(taskId);
        entity.setOwnerType(ownerType);
        entity.setAttachmentCode(code);
        entity.setFileName(id + ".pdf");
        entity.setContentType("application/pdf");
        entity.setSizeBytes(Long.valueOf(10));
        entity.setStorageKey(storageKey);
        entity.setUploadedBy("uploader");
        entity.setUploadedAt(uploadedAt);
        return entity;
    }

    private static void executeSchema(Connection connection) throws IOException, SQLException {
        String sql;
        try (InputStream input = ProcessAttachmentRepositoryIntegrationTest.class.getResourceAsStream(SCHEMA)) {
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
