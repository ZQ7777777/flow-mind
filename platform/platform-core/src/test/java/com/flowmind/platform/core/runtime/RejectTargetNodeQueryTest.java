package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.spi.OrganizationProvider;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.TaskGroupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RejectTargetNodeQueryTest {

    @Test
    void returnsConfiguredHistoricalAndCurrentlyReachableUserTasksInConfiguredOrder() {
        ProcessInstanceRepository instances = mock(ProcessInstanceRepository.class);
        ProcessHistoryTaskRepository histories = mock(ProcessHistoryTaskRepository.class);
        RuntimeNodeAdvancer advancer = mock(RuntimeNodeAdvancer.class);
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setId("instance-1");
        when(instances.findById("instance-1")).thenReturn(instance);
        when(histories.findByInstanceId("instance-1")).thenReturn(Arrays.asList(
                history("review"), history("not-reachable"), history("apply"), history("not-configured")));

        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setNodes(Arrays.asList(
                node("current", "Current", "{\"taskActionRules\":{\"reject\":{\"enabled\":true,"
                        + "\"targetNodeCodes\":[\"review\",\"not-history\",\"not-reachable\",\"apply\"]}}}"),
                node("apply", "Application", null),
                node("review", "Initial Review", null),
                node("not-history", "Not In History", null),
                node("not-reachable", "Condition Blocked", null),
                node("not-configured", "Not Configured", null)));
        when(advancer.reachableUserTaskNodeCodes(instance, definition)).thenReturn(
                new LinkedHashSet<String>(Arrays.asList("review", "not-history", "apply", "not-configured")));

        EnhancedTaskActionCoordinator coordinator = new EnhancedTaskActionCoordinator(
                instances, mock(ActiveTaskRepository.class), histories, mock(TaskGroupRepository.class),
                mock(RuntimeDefinitionLoader.class), mock(RuntimeRequestValidator.class),
                mock(RuntimeOperationExecutor.class), advancer, mock(RuntimeStateValidator.class),
                mock(com.flowmind.platform.core.task.HistoryTaskWriter.class),
                mock(RuntimeTransactionExecutor.class), mock(com.flowmind.platform.api.service.CallbackService.class),
                organizationProvider(), mock(com.flowmind.platform.core.audit.AuditLogWriter.class));

        ProcessActiveTaskEntity task = new ProcessActiveTaskEntity();
        task.setInstanceId("instance-1");
        task.setNodeCode("current");

        List<ProcessNodeDTO> targets = coordinator.getRejectTargetNodes(task, definition);

        assertThat(targets).extracting(ProcessNodeDTO::getNodeCode)
                .containsExactly("review", "apply");
        assertThat(targets).extracting(ProcessNodeDTO::getNodeName)
                .containsExactly("Initial Review", "Application");
    }

    private ProcessNodeDTO node(String code, String name, String listenerConfig) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(code);
        node.setNodeName(name);
        node.setNodeType(NodeTypeEnum.USER_TASK);
        node.setListenerConfig(listenerConfig);
        return node;
    }

    private ProcessHistoryTaskEntity history(String nodeCode) {
        ProcessHistoryTaskEntity history = new ProcessHistoryTaskEntity();
        history.setNodeCode(nodeCode);
        return history;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<OrganizationProvider> organizationProvider() {
        return mock(ObjectProvider.class);
    }
}
