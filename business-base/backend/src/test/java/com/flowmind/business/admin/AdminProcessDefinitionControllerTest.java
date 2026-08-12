package com.flowmind.business.admin;

import com.flowmind.platform.api.request.CopyProcessDefinitionRequest;
import com.flowmind.platform.api.request.CreateProcessDefinitionRequest;
import com.flowmind.platform.api.request.DefinitionOperationRequest;
import com.flowmind.platform.api.request.SaveProcessGraphRequest;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminProcessDefinitionControllerTest {

    private ProcessDefinitionService service;
    private AdminProcessDefinitionController controller;

    @BeforeEach
    void setUp() {
        service = mock(ProcessDefinitionService.class);
        AdminAccessGuard guard = mock(AdminAccessGuard.class);
        when(guard.requireAdministrator()).thenReturn("trusted-admin");
        controller = new AdminProcessDefinitionController(service, guard);
    }

    @Test
    void injectsTrustedOperatorIntoEveryDefinitionMutation() {
        CreateProcessDefinitionRequest create = new CreateProcessDefinitionRequest();
        create.setOperatorUserId("spoofed");
        controller.create(create);
        assertThat(create.getOperatorUserId()).isEqualTo("trusted-admin");

        SaveProcessGraphRequest graph = new SaveProcessGraphRequest();
        graph.setOperatorUserId("spoofed");
        controller.saveGraph("definition-1", graph);
        assertThat(graph.getOperatorUserId()).isEqualTo("trusted-admin");

        CopyProcessDefinitionRequest copy = new CopyProcessDefinitionRequest();
        copy.setOperatorUserId("spoofed");
        controller.copy("definition-1", copy);
        assertThat(copy.getOperatorUserId()).isEqualTo("trusted-admin");

        DefinitionOperationRequest operation = new DefinitionOperationRequest();
        operation.setDefinitionId("spoofed-definition");
        operation.setOperatorUserId("spoofed");
        controller.publish("definition-1", operation);
        assertThat(operation.getDefinitionId()).isEqualTo("definition-1");
        assertThat(operation.getOperatorUserId()).isEqualTo("trusted-admin");
    }

    @Test
    void dispatchesCompleteDefinitionLifecycleToPlatformService() {
        DefinitionOperationRequest publish = new DefinitionOperationRequest();
        DefinitionOperationRequest activate = new DefinitionOperationRequest();
        DefinitionOperationRequest deactivate = new DefinitionOperationRequest();
        DefinitionOperationRequest archive = new DefinitionOperationRequest();
        DefinitionOperationRequest delete = new DefinitionOperationRequest();
        controller.publish("definition-1", publish);
        controller.activate("definition-1", activate);
        controller.deactivate("definition-1", deactivate);
        controller.archive("definition-1", archive);
        controller.delete("definition-1", delete);
        verify(service).publish(publish);
        verify(service).activate(activate);
        verify(service).deactivate(deactivate);
        verify(service).archive(archive);
        verify(service).deleteDefinition(delete);
    }
}
