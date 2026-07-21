package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.api.dto.ProcessDefinitionQuery;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import com.flowmind.platform.persistence.entity.ProcessDefinitionEntity;
import com.flowmind.platform.persistence.entity.ProcessEdgeEntity;
import com.flowmind.platform.persistence.entity.ProcessNodeEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
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
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionRepositoryIntegrationTest {

    private static final String SCHEMA = "/schema/sqlite/001_init_flow_platform.sql";

    private Connection connection;
    private JdbcTemplate jdbcTemplate;
    private ProcessDefinitionRepository definitionRepository;
    private ProcessNodeRepository nodeRepository;
    private ProcessEdgeRepository edgeRepository;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        executeSchema(connection);
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
        jdbcTemplate = new JdbcTemplate(dataSource);
        definitionRepository = new ProcessDefinitionRepository(jdbcTemplate);
        nodeRepository = new ProcessNodeRepository(jdbcTemplate);
        edgeRepository = new ProcessEdgeRepository(jdbcTemplate);
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void definitionRepositoryInsertsReadsSearchesAndDeletesDefinitions() {
        LocalDateTime ten = LocalDateTime.of(2026, 7, 17, 10, 0);
        LocalDateTime eleven = LocalDateTime.of(2026, 7, 17, 11, 0);
        definitionRepository.insert(definition("definition-001", "reimburse", 1, "Reimburse V1", ten));
        definitionRepository.insert(definition("definition-002", "reimburse", 2, "Reimburse V2", eleven));
        definitionRepository.insert(definition("definition-003", "deposit", 1, "Deposit V1", eleven));

        ProcessDefinitionEntity loaded = definitionRepository.findById("definition-001");
        assertEquals("reimburse", loaded.getProcessCode());
        assertEquals(Integer.valueOf(1), loaded.getVersion());
        assertEquals("DRAFT", loaded.getDefinitionStatus());
        assertEquals("OFF", loaded.getGrayStatus());
        assertEquals(ten, loaded.getCreatedAt());
        assertEquals(ten, loaded.getUpdatedAt());

        assertEquals(Integer.valueOf(2), definitionRepository.findMaxVersionByProcessCode("reimburse"));
        assertNull(definitionRepository.findMaxVersionByProcessCode("missing"));

        ProcessDefinitionQuery query = new ProcessDefinitionQuery();
        query.setSystemCode("demo");
        query.setPageNo(Integer.valueOf(1));
        query.setPageSize(Integer.valueOf(3));
        List<ProcessDefinitionEntity> searched = definitionRepository.searchByQuery(query);
        assertEquals(3, searched.size());
        assertEquals("definition-003", searched.get(0).getId());
        assertEquals("definition-002", searched.get(1).getId());
        assertEquals("definition-001", searched.get(2).getId());
        assertEquals(3L, definitionRepository.countByQuery(query));

        ProcessDefinitionQuery filtered = new ProcessDefinitionQuery();
        filtered.setProcessCode("reimburse");
        filtered.setDefinitionStatus(DefinitionStatusEnum.DRAFT);
        assertEquals(2L, definitionRepository.countByQuery(filtered));

        ProcessDefinitionEntity update = new ProcessDefinitionEntity();
        update.setId("definition-001");
        update.setProcessName("Reimburse Updated");
        update.setSystemCode("demo-updated");
        update.setRemark("changed");
        update.setUpdatedBy("operator-updated");
        update.setUpdatedAt(LocalDateTime.of(2026, 7, 17, 12, 0));
        assertEquals(1, definitionRepository.updateBasicInfo(update));
        ProcessDefinitionEntity updated = definitionRepository.findById("definition-001");
        assertEquals("Reimburse Updated", updated.getProcessName());
        assertEquals("demo-updated", updated.getSystemCode());
        assertEquals("changed", updated.getRemark());

        assertEquals(1, definitionRepository.touchUpdated("definition-001", "operator-touch"));
        assertEquals("operator-touch", definitionRepository.findById("definition-001").getUpdatedBy());

        assertEquals(1, definitionRepository.deleteById("definition-001"));
        assertNull(definitionRepository.findById("definition-001"));
    }

    @Test
    void definitionVersionUniquenessIsEnforced() {
        definitionRepository.insert(definition("definition-001", "reimburse", 1, "Reimburse V1",
                LocalDateTime.of(2026, 7, 17, 10, 0)));

        assertThrows(DataAccessException.class,
                () -> definitionRepository.insert(definition("definition-duplicate", "reimburse", 1,
                        "Duplicate", LocalDateTime.of(2026, 7, 17, 10, 1))));
    }

    @Test
    void nodeRepositoryBatchInsertsReadsStableOrderAndDeletes() {
        definitionRepository.insert(definition("definition-001", "reimburse", 1, "Reimburse V1",
                LocalDateTime.of(2026, 7, 17, 10, 0)));

        nodeRepository.batchInsert(Arrays.asList(
                node("node-end", "definition-001", "end", "END", 3),
                node("node-approve", "definition-001", "approve", "USER_TASK", 2),
                node("node-start", "definition-001", "start", "START", 1),
                node("node-apply", "definition-001", "apply", "USER_TASK", 2)));

        List<ProcessNodeEntity> nodes = nodeRepository.findByDefinitionId("definition-001");
        assertEquals(Arrays.asList("start", "apply", "approve", "end"), Arrays.asList(
                nodes.get(0).getNodeCode(),
                nodes.get(1).getNodeCode(),
                nodes.get(2).getNodeCode(),
                nodes.get(3).getNodeCode()));
        assertEquals("{\"users\":[\"u1\"]}", nodes.get(1).getApproverRuleConfig());
        assertEquals(Double.valueOf(20.0D), nodes.get(1).getPositionX());
        assertEquals("SINGLE", nodes.get(1).getMultiInstanceMode());

        assertThrows(DataAccessException.class,
                () -> nodeRepository.batchInsert(Arrays.asList(
                        node("node-duplicate", "definition-001", "apply", "USER_TASK", 4))));

        assertEquals(4, nodeRepository.deleteByDefinitionId("definition-001"));
        assertTrue(nodeRepository.findByDefinitionId("definition-001").isEmpty());
    }

    @Test
    void edgeRepositoryBatchInsertsReadsStableOrderAndDeletes() {
        definitionRepository.insert(definition("definition-001", "reimburse", 1, "Reimburse V1",
                LocalDateTime.of(2026, 7, 17, 10, 0)));
        nodeRepository.batchInsert(Arrays.asList(
                node("node-start", "definition-001", "start", "START", 1),
                node("node-apply", "definition-001", "apply", "USER_TASK", 2),
                node("node-approve", "definition-001", "approve", "USER_TASK", 3),
                node("node-end", "definition-001", "end", "END", 4)));

        edgeRepository.batchInsert(Arrays.asList(
                edge("edge-003", "definition-001", "toEnd", "approve", "end", 3, false),
                edge("edge-001", "definition-001", "toApply", "start", "apply", 1, true),
                edge("edge-002", "definition-001", "toApprove", "apply", "approve", 2, false),
                edge("edge-004", "definition-001", "backup", "apply", "end", 2, false)));

        List<ProcessEdgeEntity> edges = edgeRepository.findByDefinitionId("definition-001");
        assertEquals(Arrays.asList("toApply", "backup", "toApprove", "toEnd"), Arrays.asList(
                edges.get(0).getEdgeCode(),
                edges.get(1).getEdgeCode(),
                edges.get(2).getEdgeCode(),
                edges.get(3).getEdgeCode()));
        assertTrue(edges.get(0).getDefaultEdge().booleanValue());
        assertFalse(edges.get(1).getDefaultEdge().booleanValue());
        assertEquals("${amount > 0}", edges.get(2).getConditionExpression());

        assertThrows(DataAccessException.class,
                () -> edgeRepository.batchInsert(Arrays.asList(
                        edge("edge-duplicate", "definition-001", "toApply", "start", "end", 4, false))));

        assertEquals(4, edgeRepository.deleteByDefinitionId("definition-001"));
        assertTrue(edgeRepository.findByDefinitionId("definition-001").isEmpty());
    }

    @Test
    void definitionRepositoryDetectsRelatedInstances() {
        definitionRepository.insert(definition("definition-001", "reimburse", 1, "Reimburse V1",
                LocalDateTime.of(2026, 7, 17, 10, 0)));

        assertFalse(definitionRepository.existsInstanceByDefinitionId("definition-001"));
        jdbcTemplate.update("INSERT INTO process_instance "
                        + "(id, definition_id, process_code, process_name, version, instance_title, "
                        + "starter_user_id, starter_user_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "instance-001", "definition-001", "reimburse", "Reimburse V1", 1,
                "Reimburse Instance", "starter", "Starter");

        assertTrue(definitionRepository.existsInstanceByDefinitionId("definition-001"));
    }

    private ProcessDefinitionEntity definition(String id, String processCode, int version, String processName,
                                               LocalDateTime time) {
        ProcessDefinitionEntity entity = new ProcessDefinitionEntity();
        entity.setId(id);
        entity.setProcessCode(processCode);
        entity.setProcessName(processName);
        entity.setSystemCode("demo");
        entity.setVersion(Integer.valueOf(version));
        entity.setDefinitionStatus("DRAFT");
        entity.setActivationStatus("INACTIVE");
        entity.setGrayStatus("OFF");
        entity.setRemark("remark-" + id);
        entity.setCreatedBy("operator");
        entity.setCreatedAt(time);
        entity.setUpdatedBy("operator");
        entity.setUpdatedAt(time);
        return entity;
    }

    private ProcessNodeEntity node(String id, String definitionId, String nodeCode, String nodeType, int sortOrder) {
        ProcessNodeEntity entity = new ProcessNodeEntity();
        entity.setId(id);
        entity.setDefinitionId(definitionId);
        entity.setNodeCode(nodeCode);
        entity.setNodeName("Node " + nodeCode);
        entity.setNodeType(nodeType);
        entity.setApproverRuleType("USER_TASK".equals(nodeType) ? "USER" : null);
        entity.setApproverRuleConfig("USER_TASK".equals(nodeType) ? "{\"users\":[\"u1\"]}" : null);
        entity.setMultiInstanceMode("SINGLE");
        entity.setListenerConfig("{\"listeners\":[]}");
        entity.setTimeoutConfig("{\"minutes\":30}");
        entity.setReminderConfig("{\"enabled\":false}");
        entity.setPositionX(Double.valueOf(sortOrder * 10.0D));
        entity.setPositionY(Double.valueOf(sortOrder * 20.0D));
        entity.setSortOrder(Integer.valueOf(sortOrder));
        return entity;
    }

    private ProcessEdgeEntity edge(String id, String definitionId, String edgeCode, String sourceNodeCode,
                                   String targetNodeCode, int sortOrder, boolean defaultEdge) {
        ProcessEdgeEntity entity = new ProcessEdgeEntity();
        entity.setId(id);
        entity.setDefinitionId(definitionId);
        entity.setEdgeCode(edgeCode);
        entity.setSourceNodeCode(sourceNodeCode);
        entity.setTargetNodeCode(targetNodeCode);
        entity.setConditionExpression("toApprove".equals(edgeCode) ? "${amount > 0}" : null);
        entity.setDefaultEdge(Boolean.valueOf(defaultEdge));
        entity.setSortOrder(Integer.valueOf(sortOrder));
        return entity;
    }

    private static void executeSchema(Connection connection) throws IOException, SQLException {
        String sql;
        try (InputStream input = DefinitionRepositoryIntegrationTest.class.getResourceAsStream(SCHEMA)) {
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
