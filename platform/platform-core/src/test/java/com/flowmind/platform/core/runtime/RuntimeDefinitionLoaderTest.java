package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.ActivationStatusEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import com.flowmind.platform.api.enums.GrayStatusEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import com.flowmind.platform.core.definition.ProcessDefinitionCache;
import com.flowmind.platform.persistence.entity.ProcessDefinitionEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.repository.ProcessDefinitionRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeDefinitionLoaderTest {

    @Test
    void startSelectsActiveDefinitionThenUsesDefinitionIdCache() {
        ProcessDefinitionRepository repository = mock(ProcessDefinitionRepository.class);
        ProcessDefinitionService definitionService = mock(ProcessDefinitionService.class);
        ProcessDefinitionEntity selected = definitionEntity("definition-v2");
        ProcessDefinitionDetailDTO definition = validDefinition("definition-v2", "expense", 2,
                DefinitionStatusEnum.PUBLISHED, ActivationStatusEnum.ACTIVE, GrayStatusEnum.OFF);
        when(repository.findActiveFullByProcessCode("expense")).thenReturn(selected);
        when(definitionService.getDefinition("definition-v2")).thenReturn(definition);

        RuntimeDefinitionLoader loader = new RuntimeDefinitionLoader(repository, definitionService,
                new ProcessDefinitionCache());

        ProcessDefinitionDetailDTO first = loader.loadForStart("expense");
        ProcessDefinitionDetailDTO second = loader.loadForStart("expense");

        assertEquals("definition-v2", first.getId());
        assertEquals("definition-v2", second.getId());
        verify(repository, times(2)).findActiveFullByProcessCode("expense");
        verify(definitionService, times(1)).getDefinition("definition-v2");
    }

    @Test
    void existingInstanceLoadsFrozenInactiveDefinitionWithoutReselectingVersion() {
        ProcessDefinitionRepository repository = mock(ProcessDefinitionRepository.class);
        ProcessDefinitionService definitionService = mock(ProcessDefinitionService.class);
        ProcessDefinitionDetailDTO retiredDefinition = validDefinition("definition-v1", "expense", 1,
                DefinitionStatusEnum.PUBLISHED, ActivationStatusEnum.INACTIVE, GrayStatusEnum.OFF);
        when(definitionService.getDefinition("definition-v1")).thenReturn(retiredDefinition);
        RuntimeDefinitionLoader loader = new RuntimeDefinitionLoader(repository, definitionService,
                new ProcessDefinitionCache());

        ProcessInstanceEntity instance = instance("definition-v1", "expense", 1);
        assertEquals("definition-v1", loader.loadForInstance(instance).getId());
        assertEquals("definition-v1", loader.loadForInstance(instance).getId());

        verify(repository, never()).findActiveFullByProcessCode(anyString());
        verify(definitionService, times(2)).getDefinition("definition-v1");
    }

    @Test
    void existingInstanceRejectsDefinitionSnapshotMismatch() {
        ProcessDefinitionRepository repository = mock(ProcessDefinitionRepository.class);
        ProcessDefinitionService definitionService = mock(ProcessDefinitionService.class);
        when(definitionService.getDefinition("definition-v1")).thenReturn(validDefinition("definition-v1", "expense", 2,
                DefinitionStatusEnum.PUBLISHED, ActivationStatusEnum.ACTIVE, GrayStatusEnum.OFF));
        RuntimeDefinitionLoader loader = new RuntimeDefinitionLoader(repository, definitionService,
                new ProcessDefinitionCache());

        RuntimeStateException exception = assertThrows(RuntimeStateException.class,
                () -> loader.loadForInstance(instance("definition-v1", "expense", 1)));

        assertEquals(RuntimeErrorCodes.DEFINITION_INVALID, exception.getErrorCode());
    }

    @Test
    void startRejectsDefinitionThatChangedToGrayBeforeCacheFill() {
        ProcessDefinitionRepository repository = mock(ProcessDefinitionRepository.class);
        ProcessDefinitionService definitionService = mock(ProcessDefinitionService.class);
        when(repository.findActiveFullByProcessCode("expense")).thenReturn(definitionEntity("definition-v2"));
        when(definitionService.getDefinition("definition-v2")).thenReturn(validDefinition("definition-v2", "expense", 2,
                DefinitionStatusEnum.PUBLISHED, ActivationStatusEnum.ACTIVE, GrayStatusEnum.ON));
        RuntimeDefinitionLoader loader = new RuntimeDefinitionLoader(repository, definitionService,
                new ProcessDefinitionCache());

        RuntimeStateException exception = assertThrows(RuntimeStateException.class,
                () -> loader.loadForStart("expense"));

        assertEquals(RuntimeErrorCodes.DEFINITION_NOT_ACTIVE, exception.getErrorCode());
    }

    private ProcessDefinitionEntity definitionEntity(String id) {
        ProcessDefinitionEntity entity = new ProcessDefinitionEntity();
        entity.setId(id);
        entity.setProcessCode("expense");
        entity.setVersion(Integer.valueOf("definition-v1".equals(id) ? 1 : 2));
        return entity;
    }

    private ProcessInstanceEntity instance(String definitionId, String processCode, int version) {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setDefinitionId(definitionId);
        instance.setProcessCode(processCode);
        instance.setVersion(Integer.valueOf(version));
        return instance;
    }

    private ProcessDefinitionDetailDTO validDefinition(String id, String code, int version,
                                                        DefinitionStatusEnum definitionStatus,
                                                        ActivationStatusEnum activationStatus,
                                                        GrayStatusEnum grayStatus) {
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setId(id);
        definition.setProcessCode(code);
        definition.setProcessName("Expense");
        definition.setVersion(Integer.valueOf(version));
        definition.setDefinitionStatus(definitionStatus);
        definition.setActivationStatus(activationStatus);
        definition.setGrayStatus(grayStatus);

        ProcessNodeDTO start = new ProcessNodeDTO();
        start.setDefinitionId(id);
        start.setNodeCode("start");
        start.setNodeName("Start");
        start.setNodeType(NodeTypeEnum.START);
        ProcessNodeDTO end = new ProcessNodeDTO();
        end.setDefinitionId(id);
        end.setNodeCode("end");
        end.setNodeName("End");
        end.setNodeType(NodeTypeEnum.END);
        definition.getNodes().add(start);
        definition.getNodes().add(end);
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setDefinitionId(id);
        edge.setEdgeCode("start-end");
        edge.setSourceNodeCode("start");
        edge.setTargetNodeCode("end");
        edge.setSortOrder(Integer.valueOf(1));
        definition.getEdges().add(edge);
        return definition;
    }
}
