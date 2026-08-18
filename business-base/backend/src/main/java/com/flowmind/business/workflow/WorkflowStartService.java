package com.flowmind.business.workflow;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import com.flowmind.platform.api.enums.GrayStatusEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.request.AttachmentUploadItem;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 通用业务发起页上下文和启动提交服务。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@Service
public class WorkflowStartService {

    private final ProcessDefinitionService definitionService;
    private final ProcessRuntimeService runtimeService;
    private final CurrentBusinessUserProvider currentUserProvider;
    private final OperationIdFactory operationIdFactory;
    private final WorkflowFormValueValidator formValueValidator;
    private final ObjectMapper objectMapper;

    public WorkflowStartService(ProcessDefinitionService definitionService,
                                ProcessRuntimeService runtimeService,
                                CurrentBusinessUserProvider currentUserProvider,
                                OperationIdFactory operationIdFactory,
                                WorkflowFormValueValidator formValueValidator,
                                ObjectMapper objectMapper) {
        this.definitionService = definitionService;
        this.runtimeService = runtimeService;
        this.currentUserProvider = currentUserProvider;
        this.operationIdFactory = operationIdFactory;
        this.formValueValidator = formValueValidator;
        this.objectMapper = objectMapper;
    }

    public WorkflowStartContextResponse startContext(String processCode) {
        String normalizedCode = requireText(processCode, "processCode");
        currentUserProvider.currentUser();
        ProcessDefinitionDTO active = searchDefinition(normalizedCode, true);
        if (active == null) {
            ProcessDefinitionDTO latest = searchDefinition(normalizedCode, false);
            if (latest == null) {
                throw new BusinessApiException(HttpStatus.NOT_FOUND, "BUSINESS_PROCESS_DEFINITION_NOT_FOUND",
                        "流程定义不存在");
            }
            WorkflowStartContextResponse disabled = new WorkflowStartContextResponse();
            disabled.setDefinitionId(latest.getId());
            disabled.setProcessCode(latest.getProcessCode());
            disabled.setProcessName(latest.getProcessName());
            disabled.setDefinitionVersion(latest.getVersion());
            disabled.setStartable(Boolean.FALSE);
            disabled.setDisabledReason("流程尚未发布并激活");
            return disabled;
        }
        ProcessDefinitionDetailDTO definition = definitionService.getDefinition(active.getId());
        ProcessNodeDTO starterNode = starterNode(definition);
        WorkflowStartContextResponse response = baseContext(definition);
        if (starterNode == null) {
            response.setStartable(Boolean.FALSE);
            response.setDisabledReason("流程定义不支持启动并提交申请节点");
            return response;
        }
        response.setCurrentNodeCode(starterNode.getNodeCode());
        response.setStartable(Boolean.TRUE);
        response.setFormFields(formFields(definition));
        response.setAttachments(attachmentRules(definition, starterNode.getNodeCode()));
        return response;
    }

    @Transactional
    public WorkflowStartSubmitResponse startAndSubmit(String processCode,
                                                      WorkflowStartSubmitRequest payload,
                                                      MultiValueMap<String, MultipartFile> multipartFiles,
                                                      String idempotencyKey) {
        String normalizedCode = requireText(processCode, "processCode");
        requireText(idempotencyKey, "Idempotency-Key");
        if (payload == null || payload.getVariables() == null) {
            throw new IllegalArgumentException("payload.variables is required");
        }
        ProcessDefinitionDTO active = searchDefinition(normalizedCode, true);
        if (active == null) {
            throw new BusinessApiException(HttpStatus.CONFLICT, "BUSINESS_PROCESS_NOT_STARTABLE",
                    "流程尚未发布并激活");
        }
        ProcessDefinitionDetailDTO definition = definitionService.getDefinition(active.getId());
        ProcessNodeDTO starterNode = starterNode(definition);
        if (starterNode == null) {
            throw new BusinessApiException(HttpStatus.CONFLICT, "BUSINESS_PROCESS_NOT_STARTABLE",
                    "流程定义不支持启动并提交申请节点");
        }
        Map<String, Object> variables = formValueValidator.validateStart(definition, payload.getVariables());
        List<AttachmentUploadItem> attachments = validateAndMapAttachments(definition, starterNode.getNodeCode(),
                multipartFiles == null ? Collections.<String, List<MultipartFile>>emptyMap() : multipartFiles);
        CurrentBusinessUserProvider.BusinessUser user = currentUserProvider.currentUser();
        String businessKey = optionalText(payload.getBusinessKey(), "businessKey", 128);

        StartProcessRequest request = new StartProcessRequest();
        request.setProcessCode(definition.getProcessCode());
        request.setBusinessKey(businessKey);
        request.setInstanceTitle(businessKey == null ? definition.getProcessName()
                : definition.getProcessName() + " - " + businessKey);
        request.setStarterUserId(user.getUserId());
        request.setStarterDeptId(user.getDepartmentId());
        request.setOperationId(operationIdFactory.create("workflow", "start-submit", normalizedCode,
                user.getUserId(), idempotencyKey));
        request.setVariables(variables);
        request.setAttachments(attachments);
        return toSubmitResponse(runtimeService.startAndSubmit(request));
    }

    private ProcessDefinitionDTO searchDefinition(String processCode, boolean activeOnly) {
        ProcessDefinitionQuery query = new ProcessDefinitionQuery();
        query.setPageNo(1);
        query.setPageSize(1);
        query.setProcessCode(processCode);
        if (activeOnly) {
            query.setDefinitionStatus(DefinitionStatusEnum.PUBLISHED);
            query.setActivationStatus(ActivationStatusEnum.ACTIVE);
            query.setGrayStatus(GrayStatusEnum.OFF);
        }
        PageResult<ProcessDefinitionDTO> page = definitionService.searchDefinitions(query);
        return page == null || page.getRecords() == null || page.getRecords().isEmpty()
                ? null : page.getRecords().get(0);
    }

    private ProcessNodeDTO starterNode(ProcessDefinitionDetailDTO definition) {
        if (definition == null || definition.getNodes() == null || definition.getEdges() == null) {
            return null;
        }
        ProcessNodeDTO start = null;
        Map<String, ProcessNodeDTO> nodes = new LinkedHashMap<String, ProcessNodeDTO>();
        for (ProcessNodeDTO node : definition.getNodes()) {
            if (node == null) continue;
            nodes.put(node.getNodeCode(), node);
            if (NodeTypeEnum.START.equals(node.getNodeType())) start = node;
        }
        if (start == null) return null;
        ProcessNodeDTO target = null;
        int outgoing = 0;
        for (ProcessEdgeDTO edge : definition.getEdges()) {
            if (edge != null && start.getNodeCode().equals(edge.getSourceNodeCode())) {
                outgoing++;
                target = nodes.get(edge.getTargetNodeCode());
            }
        }
        return outgoing == 1 && target != null && NodeTypeEnum.USER_TASK.equals(target.getNodeType())
                && ApproverRuleTypeEnum.STARTER.equals(target.getApproverRuleType()) ? target : null;
    }

    private WorkflowStartContextResponse baseContext(ProcessDefinitionDetailDTO definition) {
        WorkflowStartContextResponse response = new WorkflowStartContextResponse();
        response.setDefinitionId(definition.getId());
        response.setProcessCode(definition.getProcessCode());
        response.setProcessName(definition.getProcessName());
        response.setDefinitionVersion(definition.getVersion());
        return response;
    }

    private List<WorkflowStartContextResponse.FormFieldView> formFields(ProcessDefinitionDetailDTO definition) {
        List<ProcessFormFieldDTO> fields = definition.getFormFields() == null
                ? Collections.<ProcessFormFieldDTO>emptyList()
                : new ArrayList<ProcessFormFieldDTO>(definition.getFormFields());
        fields.sort(Comparator.comparing(ProcessFormFieldDTO::getSortOrder,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(ProcessFormFieldDTO::getFieldCode,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<WorkflowStartContextResponse.FormFieldView> result =
                new ArrayList<WorkflowStartContextResponse.FormFieldView>();
        for (ProcessFormFieldDTO field : fields) {
            if (field == null) continue;
            WorkflowStartContextResponse.FormFieldView view = new WorkflowStartContextResponse.FormFieldView();
            view.setFieldCode(field.getFieldCode());
            view.setFieldName(field.getFieldName());
            view.setFieldType(field.getFieldType());
            view.setControlType(field.getControlType());
            view.setDefaultValue(field.getDefaultValue());
            view.setValidationRule(field.getValidationRule());
            view.setOptions(options(field.getValidationRule()));
            view.setVisible(Boolean.TRUE);
            view.setEditable(Boolean.TRUE);
            view.setRequired(Boolean.TRUE.equals(field.getRequired()));
            view.setSortOrder(field.getSortOrder());
            result.add(view);
        }
        return result;
    }

    private List<WorkflowStartContextResponse.OptionView> options(String validationRule) {
        if (!hasText(validationRule)) return Collections.emptyList();
        try {
            JsonNode parsed = objectMapper.readTree(validationRule);
            JsonNode source = parsed.isArray() ? parsed : parsed.path("options");
            if (!source.isArray()) return Collections.emptyList();
            List<WorkflowStartContextResponse.OptionView> result =
                    new ArrayList<WorkflowStartContextResponse.OptionView>();
            for (JsonNode option : source) {
                JsonNode valueNode = option.isObject() ? option.get("value") : option;
                if (valueNode == null || valueNode.isNull()) continue;
                WorkflowStartContextResponse.OptionView view = new WorkflowStartContextResponse.OptionView();
                view.setValue(valueNode.asText());
                JsonNode labelNode = option.isObject() ? option.get("label") : option;
                view.setLabel(labelNode == null || labelNode.isNull() ? valueNode.asText() : labelNode.asText());
                result.add(view);
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalArgumentException("字段校验规则无效", exception);
        }
    }

    private List<WorkflowStartContextResponse.AttachmentRuleView> attachmentRules(
            ProcessDefinitionDetailDTO definition, String nodeCode) {
        List<ProcessAttachmentTemplateDTO> templates = definition.getAttachmentTemplates() == null
                ? Collections.<ProcessAttachmentTemplateDTO>emptyList()
                : new ArrayList<ProcessAttachmentTemplateDTO>(definition.getAttachmentTemplates());
        templates.sort(Comparator.comparing(ProcessAttachmentTemplateDTO::getSortOrder,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(
                ProcessAttachmentTemplateDTO::getAttachmentCode, Comparator.nullsLast(Comparator.naturalOrder())));
        List<WorkflowStartContextResponse.AttachmentRuleView> result =
                new ArrayList<WorkflowStartContextResponse.AttachmentRuleView>();
        for (ProcessAttachmentTemplateDTO template : templates) {
            if (!applicable(template, nodeCode)) continue;
            WorkflowStartContextResponse.AttachmentRuleView view =
                    new WorkflowStartContextResponse.AttachmentRuleView();
            view.setAttachmentCode(template.getAttachmentCode());
            view.setAttachmentName(template.getAttachmentName());
            view.setDescription(template.getDescription());
            view.setRequired(Boolean.TRUE.equals(template.getRequired()));
            view.setMinCount(template.getMinCount());
            view.setMaxCount(template.getMaxCount());
            view.setMaxSizeBytes(template.getMaxSizeBytes());
            view.setAllowedExtensions(template.getAllowedExtensions() == null
                    ? Collections.<String>emptyList() : new ArrayList<String>(template.getAllowedExtensions()));
            view.setSortOrder(template.getSortOrder());
            result.add(view);
        }
        return result;
    }

    private List<AttachmentUploadItem> validateAndMapAttachments(ProcessDefinitionDetailDTO definition,
                                                                  String nodeCode,
                                                                  Map<String, List<MultipartFile>> filesByPart) {
        List<WorkflowStartContextResponse.AttachmentRuleView> rules = attachmentRules(definition, nodeCode);
        Map<String, WorkflowStartContextResponse.AttachmentRuleView> byCode =
                new LinkedHashMap<String, WorkflowStartContextResponse.AttachmentRuleView>();
        for (WorkflowStartContextResponse.AttachmentRuleView rule : rules) byCode.put(rule.getAttachmentCode(), rule);
        for (String partName : filesByPart.keySet()) {
            if (!"payload".equals(partName) && !byCode.containsKey(partName)) {
                throw new IllegalArgumentException("未声明的附件编码: " + partName);
            }
        }
        List<AttachmentUploadItem> result = new ArrayList<AttachmentUploadItem>();
        for (WorkflowStartContextResponse.AttachmentRuleView rule : rules) {
            List<MultipartFile> files = filesByPart.get(rule.getAttachmentCode());
            if (files == null) files = Collections.emptyList();
            validateCount(rule, files.size());
            for (MultipartFile file : files) result.add(toAttachment(rule, file));
        }
        return result;
    }

    private void validateCount(WorkflowStartContextResponse.AttachmentRuleView rule, int count) {
        int minimum = rule.getMinCount() == null ? (Boolean.TRUE.equals(rule.getRequired()) ? 1 : 0)
                : rule.getMinCount().intValue();
        if (count < minimum) {
            throw new IllegalArgumentException("附件数量不足: " + rule.getAttachmentCode());
        }
        if (rule.getMaxCount() != null && count > rule.getMaxCount().intValue()) {
            throw new IllegalArgumentException("附件数量超过限制: " + rule.getAttachmentCode());
        }
    }

    private AttachmentUploadItem toAttachment(WorkflowStartContextResponse.AttachmentRuleView rule,
                                              MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("附件文件不能为空: " + rule.getAttachmentCode());
        }
        String extension = extension(file.getOriginalFilename());
        Set<String> allowed = new LinkedHashSet<String>();
        if (rule.getAllowedExtensions() != null) {
            for (String item : rule.getAllowedExtensions()) {
                if (item != null) allowed.add(item.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\.", ""));
            }
        }
        if (!allowed.isEmpty() && !allowed.contains(extension)) {
            throw new IllegalArgumentException("附件文件格式不支持: " + rule.getAttachmentCode());
        }
        if (rule.getMaxSizeBytes() != null && file.getSize() > rule.getMaxSizeBytes().longValue()) {
            throw new IllegalArgumentException("附件文件大小超过限制: " + rule.getAttachmentCode());
        }
        AttachmentUploadItem item = new AttachmentUploadItem();
        item.setAttachmentCode(rule.getAttachmentCode());
        item.setOwnerType(AttachmentOwnerTypeEnum.INSTANCE);
        item.setFileName(file.getOriginalFilename());
        item.setContentType(file.getContentType());
        item.setSizeBytes(Long.valueOf(file.getSize()));
        try {
            item.setContent(file.getBytes());
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取附件内容", exception);
        }
        return item;
    }

    private boolean applicable(ProcessAttachmentTemplateDTO template, String nodeCode) {
        return template != null
                && (template.getConfigStatus() == null
                || AttachmentConfigStatusEnum.ACTIVE.equals(template.getConfigStatus()))
                && template.getApplicableNodeCodes() != null
                && template.getApplicableNodeCodes().contains(nodeCode);
    }

    private WorkflowStartSubmitResponse toSubmitResponse(ProcessInstanceDTO instance) {
        WorkflowStartSubmitResponse response = new WorkflowStartSubmitResponse();
        response.setInstanceId(instance.getInstanceId());
        response.setDefinitionId(instance.getDefinitionId());
        response.setProcessCode(instance.getProcessCode());
        response.setDefinitionVersion(instance.getVersion());
        response.setInstanceStatus(instance.getInstanceStatus() == null ? null : instance.getInstanceStatus().name());
        response.setCurrentNodeCodes(instance.getCurrentNodeCodes() == null
                ? Collections.<String>emptyList() : new ArrayList<String>(instance.getCurrentNodeCodes()));
        List<WorkflowStartSubmitResponse.CreatedTaskView> tasks =
                new ArrayList<WorkflowStartSubmitResponse.CreatedTaskView>();
        if (instance.getCreatedTasks() != null) {
            for (TaskDTO task : instance.getCreatedTasks()) {
                if (task == null) continue;
                WorkflowStartSubmitResponse.CreatedTaskView view =
                        new WorkflowStartSubmitResponse.CreatedTaskView();
                view.setTaskId(task.getTaskId());
                view.setNodeCode(task.getNodeCode());
                view.setTaskName(task.getNodeName());
                tasks.add(view);
            }
        }
        response.setCreatedTasks(tasks);
        return response;
    }

    private String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1
                ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String optionalText(String value, String fieldName, int maxLength) {
        if (!hasText(value)) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(fieldName + " is too long");
        return normalized;
    }

    private String requireText(String value, String fieldName) {
        if (!hasText(value)) throw new IllegalArgumentException(fieldName + " is required");
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
