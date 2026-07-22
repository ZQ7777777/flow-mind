package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.HandleTypeEnum;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.TaskStatusEnum;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeModelMapperTest {

    @Test
    void codecPreservesRuntimeObjectAndStringArrayStructure() {
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("amount", Integer.valueOf(100));
        variables.put("approvedAt", LocalDateTime.of(2026, 7, 22, 9, 30));

        String objectJson = RuntimeJsonCodec.toJson(variables);
        String listJson = RuntimeJsonCodec.toJson(Arrays.asList("review", "finance"));

        assertEquals(Integer.valueOf(100), RuntimeJsonCodec.readObjectMap(objectJson).get("amount"));
        assertEquals(Arrays.asList("review", "finance"), RuntimeJsonCodec.readStringList(listJson));
        assertEquals(0, RuntimeJsonCodec.readObjectMap(null).size());
        assertEquals(0, RuntimeJsonCodec.readStringList(" ").size());
        assertEquals(0, RuntimeJsonCodec.readObjectMap("null").size());
        assertEquals(0, RuntimeJsonCodec.readStringList("null").size());
        assertThrows(IllegalArgumentException.class, () -> RuntimeJsonCodec.readObjectMap("[1]"));
        assertThrows(IllegalArgumentException.class, () -> RuntimeJsonCodec.readStringList("{}"));
        assertThrows(IllegalArgumentException.class, () -> RuntimeJsonCodec.readStringList("[\"user-a\", 1]"));
    }

    @Test
    void mapperConvertsPersistedRuntimeRecordsToPublicDtos() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setId("instance-1");
        instance.setDefinitionId("definition-1");
        instance.setInstanceStatus("RUNNING");
        instance.setCurrentNodeCodes("[\"review\"]");
        instance.setVariablesJson("{\"amount\":100}");
        ProcessInstanceDTO instanceDto = RuntimeModelMapper.toDto(instance);

        ProcessActiveTaskEntity task = new ProcessActiveTaskEntity();
        task.setId("task-1");
        task.setInstanceId("instance-1");
        task.setDefinitionId("definition-1");
        task.setNodeCode("review");
        task.setCandidateUserIds("[\"user-a\"]");
        task.setTaskStatus("ACTIVE");
        task.setLockVersion(Long.valueOf(3));
        TaskDTO taskDto = RuntimeModelMapper.toDto(task, "主管审批", "委托人");

        ProcessHistoryTaskEntity history = new ProcessHistoryTaskEntity();
        history.setId("history-1");
        history.setInstanceId("instance-1");
        history.setOperationId("operation-1");
        history.setActiveTaskId("task-1");
        history.setNodeCode("review");
        history.setHandleType("NORMAL");
        history.setActionType("APPROVE");
        history.setVariablesSnapshot("{\"amount\":100}");
        HistoryTaskDTO historyDto = RuntimeModelMapper.toDto(history);

        assertEquals(InstanceStatusEnum.RUNNING, instanceDto.getInstanceStatus());
        assertEquals(Arrays.asList("review"), instanceDto.getCurrentNodeCodes());
        assertEquals(Integer.valueOf(100), instanceDto.getVariables().get("amount"));
        assertEquals(TaskStatusEnum.ACTIVE, taskDto.getTaskStatus());
        assertEquals(Arrays.asList("user-a"), taskDto.getCandidateUserIds());
        assertEquals(Long.valueOf(3), taskDto.getTaskVersion());
        assertEquals("主管审批", taskDto.getNodeName());
        assertEquals(HandleTypeEnum.NORMAL, historyDto.getHandleType());
        assertEquals(ActionTypeEnum.APPROVE, historyDto.getActionType());
        assertEquals(Integer.valueOf(100), historyDto.getVariablesSnapshot().get("amount"));
    }
}
