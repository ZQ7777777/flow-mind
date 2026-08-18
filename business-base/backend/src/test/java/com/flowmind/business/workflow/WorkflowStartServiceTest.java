package com.flowmind.business.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.business.common.BusinessApiException;
import com.flowmind.business.common.OperationIdFactory;
import com.flowmind.business.security.CurrentBusinessUserProvider;
import com.flowmind.business.workflow.dto.WorkflowStartContextResponse;
import com.flowmind.business.workflow.dto.WorkflowStartSubmitRequest;
import com.flowmind.business.workflow.dto.WorkflowStartSubmitResponse;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionQuery;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.enums.ActivationStatusEnum;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.AttachmentConfigStatusEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import com.flowmind.platform.api.enums.GrayStatusEnum;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowStartServiceTest {

    private ProcessDefinitionService definitionService;
    private ProcessRuntimeService runtimeService;
    private WorkflowStartService service;
    private ProcessDefinitionDetailDTO definition;

    @BeforeEach
    void setUp() {
        definitionService = mock(ProcessDefinitionService.class);
        runtimeService = mock(ProcessRuntimeService.class);
        CurrentBusinessUserProvider users = mock(CurrentBusinessUserProvider.class);
        when(users.currentUser()).thenReturn(new CurrentBusinessUserProvider.BusinessUser("user-1", "dept-1"));
        service = new WorkflowStartService(definitionService, runtimeService, users, new OperationIdFactory(),
                new WorkflowFormValueValidator(new ObjectMapper()), new ObjectMapper());
        definition = definition();
        when(definitionService.searchDefinitions(any(ProcessDefinitionQuery.class))).thenReturn(page(definition));
        when(definitionService.getDefinition("definition-1")).thenReturn(definition);
    }

    @Test
    void returnsDefinitionDrivenContextWithOptionsAndStarterAttachments() {
        WorkflowStartContextResponse context = service.startContext("entry_application");

        assertThat(context.getStartable()).isTrue();
        assertThat(context.getCurrentNodeCode()).isEqualTo("apply");
        assertThat(context.getFormFields()).hasSize(2);
        assertThat(context.getFormFields().get(1).getDefaultValue()).isEqualTo("CNY");
        assertThat(context.getFormFields().get(1).getOptions()).singleElement()
                .satisfies(option -> assertThat(option.getValue()).isEqualTo("CNY"));
        assertThat(context.getAttachments()).singleElement()
                .satisfies(rule -> assertThat(rule.getAttachmentCode()).isEqualTo("bankReceipt"));
    }

    @Test
    void returnsDisabledContextWhenProcessExistsWithoutActiveDefinition() {
        ProcessDefinitionDTO inactive = new ProcessDefinitionDTO();
        inactive.setId("definition-draft");
        inactive.setProcessCode("entry_application");
        inactive.setProcessName("入金申请");
        inactive.setVersion(Integer.valueOf(2));
        when(definitionService.searchDefinitions(any(ProcessDefinitionQuery.class)))
                .thenReturn(emptyPage(), page(inactive));

        WorkflowStartContextResponse context = service.startContext("entry_application");

        assertThat(context.getStartable()).isFalse();
        assertThat(context.getDefinitionId()).isEqualTo("definition-draft");
        assertThat(context.getDefinitionVersion()).isEqualTo(2);
        assertThat(context.getFormFields()).isEmpty();
        assertThat(context.getDisabledReason()).isNotBlank();
    }

    @Test
    void validatesAndMapsTrustedSubmissionBeforeSingleRuntimeCall() {
        ProcessInstanceDTO instance = new ProcessInstanceDTO();
        instance.setInstanceId("instance-1");
        instance.setDefinitionId("definition-1");
        instance.setProcessCode("entry_application");
        instance.setVersion(Integer.valueOf(1));
        instance.setInstanceStatus(InstanceStatusEnum.RUNNING);
        instance.setCurrentNodeCodes(Collections.singletonList("manager"));
        TaskDTO task = new TaskDTO(); task.setTaskId("task-1"); task.setNodeCode("manager");
        task.setNodeName("经理审批"); instance.setCreatedTasks(Collections.singletonList(task));
        when(runtimeService.startAndSubmit(any(StartProcessRequest.class))).thenReturn(instance);
        WorkflowStartSubmitRequest payload = payload();
        MultiValueMap<String, MultipartFile> files = new LinkedMultiValueMap<String, MultipartFile>();
        files.add("bankReceipt", new MockMultipartFile("bankReceipt", "receipt.pdf",
                "application/pdf", "content".getBytes()));

        WorkflowStartSubmitResponse response = service.startAndSubmit("entry_application", payload, files, "key-1");

        assertThat(response.getInstanceId()).isEqualTo("instance-1");
        ArgumentCaptor<StartProcessRequest> captor = ArgumentCaptor.forClass(StartProcessRequest.class);
        verify(runtimeService).startAndSubmit(captor.capture());
        StartProcessRequest request = captor.getValue();
        assertThat(request.getStarterUserId()).isEqualTo("user-1");
        assertThat(request.getStarterDeptId()).isEqualTo("dept-1");
        assertThat(request.getInstanceTitle()).isEqualTo("入金申请 - APP-001");
        assertThat(request.getVariables()).containsEntry("currency", "CNY");
        assertThat(request.getAttachments()).singleElement()
                .satisfies(item -> assertThat(item.getAttachmentCode()).isEqualTo("bankReceipt"));
        assertThat(request.getOperationId()).hasSize(64);
    }

    @Test
    void rejectsUnknownVariablesAndInvalidAttachmentsBeforeRuntime() {
        WorkflowStartSubmitRequest unknownField = payload();
        unknownField.getVariables().put("internalApprover", "spoofed");
        MultiValueMap<String, MultipartFile> legalFile = new LinkedMultiValueMap<String, MultipartFile>();
        legalFile.add("bankReceipt", new MockMultipartFile("bankReceipt", "receipt.pdf",
                "application/pdf", "content".getBytes()));
        assertThatThrownBy(() -> service.startAndSubmit("entry_application", unknownField, legalFile, "key-1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("未声明");

        WorkflowStartSubmitRequest legalPayload = payload();
        MultiValueMap<String, MultipartFile> invalidFile = new LinkedMultiValueMap<String, MultipartFile>();
        invalidFile.add("bankReceipt", new MockMultipartFile("bankReceipt", "receipt.exe",
                "application/octet-stream", "content".getBytes()));
        assertThatThrownBy(() -> service.startAndSubmit("entry_application", legalPayload, invalidFile, "key-2"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("格式");
        verify(runtimeService, never()).startAndSubmit(any(StartProcessRequest.class));
    }

    @Test
    void rejectsSubmissionWhenDefinitionIsNotActive() {
        when(definitionService.searchDefinitions(any(ProcessDefinitionQuery.class))).thenReturn(emptyPage());

        assertThatThrownBy(() -> service.startAndSubmit("entry_application", payload(),
                new LinkedMultiValueMap<String, MultipartFile>(), "key-1"))
                .isInstanceOf(BusinessApiException.class)
                .extracting(exception -> ((BusinessApiException) exception).getCode())
                .isEqualTo("BUSINESS_PROCESS_NOT_STARTABLE");
    }

    private WorkflowStartSubmitRequest payload() {
        WorkflowStartSubmitRequest request = new WorkflowStartSubmitRequest();
        request.setBusinessKey("APP-001");
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("applicationNo", "APP-001");
        request.setVariables(variables);
        return request;
    }

    private ProcessDefinitionDetailDTO definition() {
        ProcessDefinitionDetailDTO value = new ProcessDefinitionDetailDTO();
        value.setId("definition-1"); value.setProcessCode("entry_application"); value.setProcessName("入金申请");
        value.setVersion(Integer.valueOf(1)); value.setDefinitionStatus(DefinitionStatusEnum.PUBLISHED);
        value.setActivationStatus(ActivationStatusEnum.ACTIVE); value.setGrayStatus(GrayStatusEnum.OFF);
        ProcessNodeDTO start = node("start", NodeTypeEnum.START, null);
        ProcessNodeDTO apply = node("apply", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.STARTER);
        value.setNodes(Arrays.asList(start, apply));
        ProcessEdgeDTO edge = new ProcessEdgeDTO(); edge.setSourceNodeCode("start"); edge.setTargetNodeCode("apply");
        value.setEdges(Collections.singletonList(edge));
        ProcessFormFieldDTO applicationNo = field("applicationNo", "string", true, null, null, 1);
        ProcessFormFieldDTO currency = field("currency", "select", true, "CNY",
                "{\"options\":[{\"label\":\"人民币\",\"value\":\"CNY\"}]}", 2);
        value.setFormFields(Arrays.asList(applicationNo, currency));
        ProcessAttachmentTemplateDTO attachment = new ProcessAttachmentTemplateDTO();
        attachment.setAttachmentCode("bankReceipt"); attachment.setAttachmentName("银行回单");
        attachment.setConfigStatus(AttachmentConfigStatusEnum.ACTIVE); attachment.setRequired(Boolean.TRUE);
        attachment.setMinCount(Integer.valueOf(1)); attachment.setMaxCount(Integer.valueOf(2));
        attachment.setMaxSizeBytes(Long.valueOf(1024)); attachment.setAllowedExtensions(Collections.singletonList("pdf"));
        attachment.setApplicableNodeCodes(Collections.singletonList("apply")); attachment.setSortOrder(Integer.valueOf(1));
        value.setAttachmentTemplates(Collections.singletonList(attachment));
        return value;
    }

    private ProcessNodeDTO node(String code, NodeTypeEnum type, ApproverRuleTypeEnum rule) {
        ProcessNodeDTO node = new ProcessNodeDTO(); node.setNodeCode(code); node.setNodeType(type);
        node.setApproverRuleType(rule); return node;
    }

    private ProcessFormFieldDTO field(String code, String type, boolean required, String defaultValue,
                                      String validationRule, int order) {
        ProcessFormFieldDTO field = new ProcessFormFieldDTO(); field.setFieldCode(code); field.setFieldName(code);
        field.setFieldType(type); field.setControlType("select".equals(type) ? "select" : "input");
        field.setRequired(Boolean.valueOf(required)); field.setDefaultValue(defaultValue);
        field.setValidationRule(validationRule); field.setSortOrder(Integer.valueOf(order)); return field;
    }

    private <T extends ProcessDefinitionDTO> PageResult<ProcessDefinitionDTO> page(T value) {
        PageResult<ProcessDefinitionDTO> page = new PageResult<ProcessDefinitionDTO>();
        page.setRecords(Collections.<ProcessDefinitionDTO>singletonList(value)); return page;
    }

    private PageResult<ProcessDefinitionDTO> emptyPage() {
        PageResult<ProcessDefinitionDTO> page = new PageResult<ProcessDefinitionDTO>();
        page.setRecords(Collections.<ProcessDefinitionDTO>emptyList()); return page;
    }
}
