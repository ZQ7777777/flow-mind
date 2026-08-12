package com.flowmind.business.workflow;

import com.flowmind.business.common.OperationIdFactory;
import com.flowmind.business.platform.PlatformDtoMapper;
import com.flowmind.business.platform.PlatformFacade;
import com.flowmind.business.workflow.dto.WorkflowActionRequests;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.request.AddSignRequest;
import com.flowmind.platform.api.request.DelegateTaskRequest;
import com.flowmind.platform.api.request.DirectSendRequest;
import com.flowmind.platform.api.request.RejectTaskRequest;
import com.flowmind.platform.api.request.TaskOperationRequest;
import com.flowmind.platform.api.request.TransferTaskRequest;
import com.flowmind.platform.api.request.SubmitTaskRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowActionServiceTest {
    private PlatformFacade facade;
    private WorkflowQueryService queryService;
    private WorkflowActionService service;
    private WorkflowFormValueValidator formValueValidator;

    @BeforeEach
    void setUp() {
        facade = mock(PlatformFacade.class);
        queryService = mock(WorkflowQueryService.class);
        formValueValidator = mock(WorkflowFormValueValidator.class);
        service = new WorkflowActionService(facade, new PlatformDtoMapper(), new OperationIdFactory(), queryService,
                formValueValidator);
        when(facade.currentUser()).thenReturn(new UserContext("manager-1", "Manager", "dept-1", "Dept"));
        ProcessInstanceDetailDTO instance = new ProcessInstanceDetailDTO();
        instance.setDefinitionId("definition-1");
        when(queryService.authorizedTaskInstance("task-1")).thenReturn(instance);
        when(facade.getTask("task-1")).thenReturn(new TaskDTO());
        when(facade.getDefinition("definition-1")).thenReturn(new ProcessDefinitionDetailDTO());
        when(formValueValidator.validate(any(ProcessDefinitionDetailDTO.class), any(TaskDTO.class),
                any(Map.class))).thenAnswer(invocation -> invocation.getArgument(2));
        TaskActionResult result = new TaskActionResult();
        result.setInstance(new ProcessInstanceDTO());
        when(facade.execute(any(String.class), any(TaskOperationRequest.class))).thenReturn(result);
    }

    @Test
    void everyActionCarriesTrustedIdentityVersionAndActionIsolatedOperationId() {
        Map<String, WorkflowActionRequests.Basic> actions = new LinkedHashMap<String, WorkflowActionRequests.Basic>();
        actions.put("approve", basic());
        WorkflowActionRequests.Submit submit = new WorkflowActionRequests.Submit(); copy(submit);
        submit.setVariables(new LinkedHashMap<String, Object>()); actions.put("submit", submit);
        actions.put("return", basic());
        actions.put("withdraw", basic()); actions.put("claim", basic()); actions.put("unclaim", basic());
        WorkflowActionRequests.Reject reject = new WorkflowActionRequests.Reject(); copy(reject); reject.setTargetNodeCode("apply"); actions.put("reject", reject);
        WorkflowActionRequests.DirectSend direct = new WorkflowActionRequests.DirectSend(); copy(direct); actions.put("direct-send", direct);
        WorkflowActionRequests.Transfer transfer = new WorkflowActionRequests.Transfer(); copy(transfer); transfer.setTargetUserId("user-2"); actions.put("transfer", transfer);
        WorkflowActionRequests.Delegate delegate = new WorkflowActionRequests.Delegate(); copy(delegate); delegate.setTargetUserId("user-3"); delegate.setTargetUserName("User 3"); actions.put("delegate", delegate);
        WorkflowActionRequests.AddSign addSign = new WorkflowActionRequests.AddSign(); copy(addSign); addSign.setAddSignUserIds(Arrays.asList("user-4", "user-4", "user-5")); actions.put("add-sign", addSign);

        Map<String, String> operationIds = new LinkedHashMap<String, String>();
        for (Map.Entry<String, WorkflowActionRequests.Basic> entry : actions.entrySet()) {
            service.execute(entry.getKey(), "task-1", "retry-key", entry.getValue());
            ArgumentCaptor<TaskOperationRequest> captor = ArgumentCaptor.forClass(TaskOperationRequest.class);
            verify(facade).execute(eq(entry.getKey()), captor.capture());
            TaskOperationRequest request = captor.getValue();
            assertThat(request.getTaskId()).isEqualTo("task-1");
            assertThat(request.getExpectedTaskVersion()).isEqualTo(7L);
            assertThat(request.getOperatorUserId()).isEqualTo("manager-1");
            operationIds.put(entry.getKey(), request.getOperationId());
        }
        assertThat(operationIds.values()).doesNotHaveDuplicates();
        verify(queryService, org.mockito.Mockito.times(actions.size() - 1)).authorizedTaskInstance("task-1");
    }

    @Test
    void mapsOnlyFrozenActionSpecificFieldsAndDoesNotExposeVariables() {
        WorkflowActionRequests.DirectSend direct = new WorkflowActionRequests.DirectSend(); copy(direct);
        service.execute("direct-send", "task-1", "key", direct);
        ArgumentCaptor<TaskOperationRequest> directCaptor = ArgumentCaptor.forClass(TaskOperationRequest.class);
        verify(facade).execute(eq("direct-send"), directCaptor.capture());
        assertThat(((DirectSendRequest) directCaptor.getValue()).getVariables()).isNull();
        assertThat(((DirectSendRequest) directCaptor.getValue()).getTargetNodeCode()).isNull();

        WorkflowActionRequests.AddSign addSign = new WorkflowActionRequests.AddSign(); copy(addSign);
        addSign.setAddSignUserIds(Arrays.asList("a", "a", "b"));
        service.execute("add-sign", "task-1", "key-2", addSign);
        ArgumentCaptor<TaskOperationRequest> addSignCaptor = ArgumentCaptor.forClass(TaskOperationRequest.class);
        verify(facade).execute(eq("add-sign"), addSignCaptor.capture());
        assertThat(((AddSignRequest) addSignCaptor.getValue()).getAddSignUserIds()).containsExactly("a", "b");
        assertThat(RejectTaskRequest.class.getMethods()).anyMatch(method -> method.getName().equals("getTargetNodeCode"));
        assertThat(TransferTaskRequest.class.getMethods()).anyMatch(method -> method.getName().equals("getTargetUserId"));
        assertThat(DelegateTaskRequest.class.getMethods()).anyMatch(method -> method.getName().equals("getTargetUserName"));
    }

    @Test
    void mapsValidatedVariablesOnlyForStarterSubmitAndDirectSend() {
        Map<String, Object> variables = new LinkedHashMap<String, Object>(); variables.put("amount", 2000);
        WorkflowActionRequests.Submit submit = new WorkflowActionRequests.Submit(); copy(submit);
        submit.setVariables(variables);
        service.execute("submit", "task-1", "submit-key", submit);
        ArgumentCaptor<TaskOperationRequest> submitCaptor = ArgumentCaptor.forClass(TaskOperationRequest.class);
        verify(facade).execute(eq("submit"), submitCaptor.capture());
        assertThat(((SubmitTaskRequest) submitCaptor.getValue()).getVariables()).containsEntry("amount", 2000);

        WorkflowActionRequests.DirectSend direct = new WorkflowActionRequests.DirectSend(); copy(direct);
        direct.setVariables(variables);
        service.execute("direct-send", "task-1", "direct-key", direct);
        ArgumentCaptor<TaskOperationRequest> directCaptor = ArgumentCaptor.forClass(TaskOperationRequest.class);
        verify(facade).execute(eq("direct-send"), directCaptor.capture());
        assertThat(((DirectSendRequest) directCaptor.getValue()).getVariables()).containsEntry("amount", 2000);
    }

    private WorkflowActionRequests.Basic basic() { WorkflowActionRequests.Basic value = new WorkflowActionRequests.Basic(); copy(value); return value; }
    private void copy(WorkflowActionRequests.Basic value) { value.setExpectedTaskVersion(7L); value.setComment("同意"); }
}
