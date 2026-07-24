package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.ApiErrorDTO;
import com.flowmind.platform.api.dto.CallbackLogDTO;
import com.flowmind.platform.api.dto.CallbackLogQuery;
import com.flowmind.platform.api.dto.OperationResult;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessDefinitionDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionQuery;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.api.request.AddSignRequest;
import com.flowmind.platform.api.request.ApproveTaskRequest;
import com.flowmind.platform.api.request.ClaimTaskRequest;
import com.flowmind.platform.api.request.CopyProcessDefinitionRequest;
import com.flowmind.platform.api.request.CreateProcessDefinitionRequest;
import com.flowmind.platform.api.request.DefinitionOperationRequest;
import com.flowmind.platform.api.request.DeleteProcessInstanceRequest;
import com.flowmind.platform.api.request.DirectSendRequest;
import com.flowmind.platform.api.request.GrayReleaseRequest;
import com.flowmind.platform.api.request.RejectTaskRequest;
import com.flowmind.platform.api.request.ReturnTaskRequest;
import com.flowmind.platform.api.request.SaveProcessGraphRequest;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.request.SubmitTaskRequest;
import com.flowmind.platform.api.request.TerminateProcessRequest;
import com.flowmind.platform.api.request.TransferTaskRequest;
import com.flowmind.platform.api.request.UnclaimTaskRequest;
import com.flowmind.platform.api.request.UpdateVariablesRequest;
import com.flowmind.platform.api.request.WithdrawTaskRequest;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.core.definition.DefinitionErrorCodes;
import com.flowmind.platform.core.definition.DefinitionValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformServiceControllerTest {

    @Test
    void exceptionHandlerMapsBusinessValidationToBadRequestBody() {
        PlatformExceptionHandler handler = new PlatformExceptionHandler();

        ResponseEntity<ApiErrorDTO> response = handler.handleDefinitionValidation(
                new DefinitionValidationException(DefinitionErrorCodes.DEFINITION_INVALID,
                        "Attachment template id, code, required flag and minCount must not be blank."));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Integer.valueOf(400), response.getBody().getStatus());
        assertEquals(DefinitionErrorCodes.DEFINITION_INVALID, response.getBody().getCode());
        assertEquals("Attachment template id, code, required flag and minCount must not be blank.",
                response.getBody().getMessage());
    }

    @Test
    void exceptionHandlerMapsSqliteBusyToRetryableServiceUnavailableBody() {
        PlatformExceptionHandler handler = new PlatformExceptionHandler();

        ResponseEntity<ApiErrorDTO> response = handler.handleDataAccess(
                new DataAccessResourceFailureException("[SQLITE_BUSY] database is locked"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(Integer.valueOf(503), response.getBody().getStatus());
        assertEquals("FLOW_DATABASE_BUSY", response.getBody().getCode());
    }

    @Test
    void definitionControllerDelegatesToDefinitionService() {
        ProcessDefinitionService service = mock(ProcessDefinitionService.class);
        ProcessDefinitionController controller = new ProcessDefinitionController(service);
        CreateProcessDefinitionRequest createRequest = new CreateProcessDefinitionRequest();
        SaveProcessGraphRequest graphRequest = new SaveProcessGraphRequest();
        DefinitionOperationRequest operationRequest = new DefinitionOperationRequest();
        GrayReleaseRequest grayRequest = new GrayReleaseRequest();
        CopyProcessDefinitionRequest copyRequest = new CopyProcessDefinitionRequest();
        ProcessDefinitionQuery query = new ProcessDefinitionQuery();
        ProcessDefinitionDTO definition = new ProcessDefinitionDTO();
        ProcessDefinitionDetailDTO detail = new ProcessDefinitionDetailDTO();
        ValidationResult validation = new ValidationResult();
        OperationResult deleteResult = new OperationResult();
        PageResult<ProcessDefinitionDTO> page = new PageResult<ProcessDefinitionDTO>();
        when(service.createDefinition(createRequest)).thenReturn(definition);
        when(service.saveGraph("definition-1", graphRequest)).thenReturn(definition);
        when(service.validateForPublish("definition-1")).thenReturn(validation);
        when(service.publish(operationRequest)).thenReturn(definition);
        when(service.activate(operationRequest)).thenReturn(definition);
        when(service.deactivate(operationRequest)).thenReturn(definition);
        when(service.archive(operationRequest)).thenReturn(definition);
        when(service.enableGray(grayRequest)).thenReturn(definition);
        when(service.disableGray(operationRequest)).thenReturn(definition);
        when(service.copyDefinition("definition-1", copyRequest)).thenReturn(definition);
        when(service.deleteDefinition(operationRequest)).thenReturn(deleteResult);
        when(service.getDefinition("definition-1")).thenReturn(detail);
        when(service.searchDefinitions(query)).thenReturn(page);
        when(service.listNodes("definition-1")).thenReturn(Collections.<ProcessNodeDTO>emptyList());

        assertEquals(definition, controller.createDefinition(createRequest));
        assertEquals(definition, controller.saveGraph("definition-1", graphRequest));
        assertEquals(validation, controller.validateForPublish("definition-1"));
        assertEquals(definition, controller.publish(operationRequest));
        assertEquals(definition, controller.activate(operationRequest));
        assertEquals(definition, controller.deactivate(operationRequest));
        assertEquals(definition, controller.archive(operationRequest));
        assertEquals(definition, controller.enableGray(grayRequest));
        assertEquals(definition, controller.disableGray(operationRequest));
        assertEquals(definition, controller.copyDefinition("definition-1", copyRequest));
        assertEquals(deleteResult, controller.deleteDefinition(operationRequest));
        assertEquals(detail, controller.getDefinition("definition-1"));
        assertEquals(page, controller.searchDefinitions(query));
        assertEquals(0, controller.listNodes("definition-1").size());

        verify(service).listNodes("definition-1");
    }

    @Test
    void runtimeControllerDelegatesToRuntimeService() {
        ProcessRuntimeService service = mock(ProcessRuntimeService.class);
        ProcessRuntimeController controller = new ProcessRuntimeController(service);
        StartProcessRequest startRequest = new StartProcessRequest();
        SubmitTaskRequest submitRequest = new SubmitTaskRequest();
        ApproveTaskRequest approveRequest = new ApproveTaskRequest();
        RejectTaskRequest rejectRequest = new RejectTaskRequest();
        ReturnTaskRequest returnRequest = new ReturnTaskRequest();
        WithdrawTaskRequest withdrawRequest = new WithdrawTaskRequest();
        DirectSendRequest directSendRequest = new DirectSendRequest();
        TransferTaskRequest transferRequest = new TransferTaskRequest();
        AddSignRequest addSignRequest = new AddSignRequest();
        ClaimTaskRequest claimRequest = new ClaimTaskRequest();
        UnclaimTaskRequest unclaimRequest = new UnclaimTaskRequest();
        TerminateProcessRequest terminateRequest = new TerminateProcessRequest();
        DeleteProcessInstanceRequest deleteRequest = new DeleteProcessInstanceRequest();
        UpdateVariablesRequest updateRequest = new UpdateVariablesRequest();
        ProcessInstanceDTO instance = new ProcessInstanceDTO();
        ProcessInstanceDetailDTO detail = new ProcessInstanceDetailDTO();
        TaskActionResult taskResult = new TaskActionResult();
        OperationResult deleteResult = new OperationResult();
        when(service.startProcess(startRequest)).thenReturn(instance);
        when(service.startAndSubmit(startRequest)).thenReturn(instance);
        when(service.submitTask(submitRequest)).thenReturn(taskResult);
        when(service.approve(approveRequest)).thenReturn(taskResult);
        when(service.reject(rejectRequest)).thenReturn(taskResult);
        when(service.returnToStarter(returnRequest)).thenReturn(taskResult);
        when(service.withdraw(withdrawRequest)).thenReturn(taskResult);
        when(service.directSend(directSendRequest)).thenReturn(taskResult);
        when(service.transfer(transferRequest)).thenReturn(taskResult);
        when(service.addSign(addSignRequest)).thenReturn(taskResult);
        when(service.claim(claimRequest)).thenReturn(taskResult);
        when(service.unclaim(unclaimRequest)).thenReturn(taskResult);
        when(service.terminate(terminateRequest)).thenReturn(instance);
        when(service.deleteInstance(deleteRequest)).thenReturn(deleteResult);
        when(service.updateVariables(updateRequest)).thenReturn(instance);
        when(service.getInstance("instance-1")).thenReturn(detail);

        assertEquals(instance, controller.startProcess(startRequest));
        assertEquals(instance, controller.startAndSubmit(startRequest));
        assertEquals(taskResult, controller.submitTask(submitRequest));
        assertEquals(taskResult, controller.approve(approveRequest));
        assertEquals(taskResult, controller.reject(rejectRequest));
        assertEquals(taskResult, controller.returnToStarter(returnRequest));
        assertEquals(taskResult, controller.withdraw(withdrawRequest));
        assertEquals(taskResult, controller.directSend(directSendRequest));
        assertEquals(taskResult, controller.transfer(transferRequest));
        assertEquals(taskResult, controller.addSign(addSignRequest));
        assertEquals(taskResult, controller.claim(claimRequest));
        assertEquals(taskResult, controller.unclaim(unclaimRequest));
        assertEquals(instance, controller.terminate(terminateRequest));
        assertEquals(deleteResult, controller.deleteInstance(deleteRequest));
        assertEquals(instance, controller.updateVariables(updateRequest));
        assertEquals(detail, controller.getInstance("instance-1"));

        verify(service).getInstance("instance-1");
    }

    @Test
    void callbackControllerDelegatesToCallbackService() {
        CallbackService service = mock(CallbackService.class);
        CallbackController controller = new CallbackController(service);
        WorkflowEvent event = new WorkflowEvent();
        CallbackLogQuery query = new CallbackLogQuery();
        PageResult<CallbackLogDTO> page = new PageResult<CallbackLogDTO>();
        when(service.queryCallbackLogs(query)).thenReturn(page);

        controller.publishCallback(event);
        assertEquals(page, controller.queryCallbackLogs(query));

        verify(service).publishCallback(event);
    }
}
