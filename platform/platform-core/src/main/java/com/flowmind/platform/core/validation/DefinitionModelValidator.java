package com.flowmind.platform.core.validation;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.enums.DefinitionErrorCodes;
import com.flowmind.platform.core.definition.DefinitionValidationException;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 流程定义模型结构的最小冻结校验器。
 *
 * <p>只报告发布前模型问题，不执行发布、部署或运行时推进。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 */
public class DefinitionModelValidator {

    private static final NodeTypeEnum USER_TASK = NodeTypeEnum.USER_TASK;
    private static final NodeTypeEnum EXCLUSIVE_GATEWAY = NodeTypeEnum.EXCLUSIVE_GATEWAY;
    private static final NodeTypeEnum PARALLEL_SPLIT_GATEWAY = NodeTypeEnum.PARALLEL_SPLIT_GATEWAY;
    private static final NodeTypeEnum PARALLEL_JOIN_GATEWAY = NodeTypeEnum.PARALLEL_JOIN_GATEWAY;
    private static final ApproverRuleTypeEnum STARTER = ApproverRuleTypeEnum.STARTER;
    /** 表单字段稳定排序规则。 */
    private static final Comparator<ProcessFormFieldDTO> FORM_FIELD_ORDER =
            new Comparator<ProcessFormFieldDTO>() {
                @Override
                public int compare(ProcessFormFieldDTO left, ProcessFormFieldDTO right) {
                    int order = compareNullableInteger(left.getSortOrder(), right.getSortOrder());
                    if (order != 0) {
                        return order;
                    }
                    order = compareNullableString(left.getFieldCode(), right.getFieldCode());
                    return order != 0 ? order
                            : compareNullableString(left.getFieldName(), right.getFieldName());
                }
            };
    /** 附件配置稳定排序规则。 */
    private static final Comparator<ProcessAttachmentTemplateDTO> ATTACHMENT_TEMPLATE_ORDER =
            new Comparator<ProcessAttachmentTemplateDTO>() {
                @Override
                public int compare(ProcessAttachmentTemplateDTO left,
                                   ProcessAttachmentTemplateDTO right) {
                    int order = compareNullableInteger(left.getSortOrder(), right.getSortOrder());
                    if (order != 0) {
                        return order;
                    }
                    order = compareNullableString(left.getAttachmentConfigId(), right.getAttachmentConfigId());
                    if (order != 0) {
                        return order;
                    }
                    order = compareNullableString(left.getAttachmentCode(), right.getAttachmentCode());
                    return order != 0 ? order
                            : compareNullableString(left.getAttachmentTemplateId(),
                            right.getAttachmentTemplateId());
                }
            };

    /**
     * 校验流程定义详情中的基础模型约束。
     *
     * @param definition 正式流程定义详情
     * @return 包含全部已发现问题的校验结果
     */
    public ValidationResult validate(ProcessDefinitionDetailDTO definition) {
        ValidationResult result = new ValidationResult();
        result.setValid(true);
        if (definition == null) {
            addIssue(result, FrozenValidationErrorCodes.MODEL_START_NODE_INVALID,
                    "Definition model must not be null.", null, null);
            return result;
        }

        DefinitionGraphIndex graph = DefinitionGraphIndex.from(definition);
        validateNullElements(result, graph);
        validateNodeCodes(result, graph.getNodes());
        validateStartAndEnd(result, graph);
        validateEdges(result, graph);
        validateSingleOutgoingNodes(result, graph);
        validateUserTasks(result, graph.getNodes());
        validateGatewayNodeConfiguration(result, graph);
        validateExclusiveGatewayTopology(result, graph);
        validateParallelGatewayTopology(result, graph);
        validateFiniteEnding(result, graph);
        validateReachability(result, graph);
        validateExtensionConfiguration(result, definition, graph);
        return result;
    }

    /**
     * 校验保存草稿图时需要立即阻断的结构问题。
     *
     * @param nodes 流程节点列表
     * @param edges 流程连线列表
     */
    public void validateSaveGraphStructure(List<ProcessNodeDTO> nodes, List<ProcessEdgeDTO> edges) {
        Set<String> nodeCodes = new LinkedHashSet<String>();
        for (ProcessNodeDTO node : nodes) {
            validateRequiredText(node.getNodeCode(), "nodeCode");
            validateRequiredText(node.getNodeName(), "nodeName");
            validateRequiredText(node.getNodeType(), "nodeType");
            if (!nodeCodes.add(node.getNodeCode())) {
                throw new DefinitionValidationException(DefinitionErrorCodes.NODE_CODE_DUPLICATED,
                        "Duplicate node code: " + node.getNodeCode());
            }
            validateNodeType(node.getNodeType());
            if (!isBlank(node.getApproverRuleType())) {
                validateApproverRuleType(node.getApproverRuleType());
            }
            validateMultiInstanceMode(node.getMultiInstanceMode());
        }

        Set<String> edgeCodes = new LinkedHashSet<String>();
        for (ProcessEdgeDTO edge : edges) {
            validateRequiredText(edge.getEdgeCode(), "edgeCode");
            validateRequiredText(edge.getSourceNodeCode(), "sourceNodeCode");
            validateRequiredText(edge.getTargetNodeCode(), "targetNodeCode");
            if (!edgeCodes.add(edge.getEdgeCode())) {
                throw new DefinitionValidationException(DefinitionErrorCodes.EDGE_CODE_DUPLICATED,
                        "Duplicate edge code: " + edge.getEdgeCode());
            }
            if (!nodeCodes.contains(edge.getSourceNodeCode())) {
                throw new DefinitionValidationException(DefinitionErrorCodes.NODE_NOT_FOUND,
                        "Edge source node does not exist: " + edge.getSourceNodeCode());
            }
            if (!nodeCodes.contains(edge.getTargetNodeCode())) {
                throw new DefinitionValidationException(DefinitionErrorCodes.NODE_NOT_FOUND,
                        "Edge target node does not exist: " + edge.getTargetNodeCode());
            }
        }

        ProcessDefinitionDetailDTO detail = new ProcessDefinitionDetailDTO();
        detail.setNodes(nodes);
        detail.setEdges(edges);
        ValidationResult result = validate(detail);
        if (!result.isValid()) {
            throw new DefinitionValidationException(DefinitionErrorCodes.DEFINITION_INVALID,
                    "process graph is invalid: " + describeIssues(result));
        }
    }

    private Map<String, ProcessNodeDTO> indexNodes(ValidationResult result, List<ProcessNodeDTO> nodes) {
        Map<String, ProcessNodeDTO> nodeByCode = new LinkedHashMap<String, ProcessNodeDTO>();
    private void validateNullElements(ValidationResult result, DefinitionGraphIndex graph) {
        for (int index = 0; index < graph.getNullNodeCount(); index++) {
            addIssue(result, FrozenValidationErrorCodes.MODEL_NODE_REFERENCE_INVALID,
                    "Definition model contains a null node.", null, null);
        }
        for (int index = 0; index < graph.getNullEdgeCount(); index++) {
            addIssue(result, FrozenValidationErrorCodes.MODEL_EDGE_REFERENCE_INVALID,
                    "Definition model contains a null edge.", null, null);
        }
    }

    private void validateNodeCodes(ValidationResult result, List<ProcessNodeDTO> nodes) {
        Set<String> nodeCodes = new LinkedHashSet<String>();
        for (ProcessNodeDTO node : nodes) {
            if (isBlank(node.getNodeCode())) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_NODE_REFERENCE_INVALID,
                        "Node code must not be blank.", null, null);
            } else if (!nodeCodes.add(node.getNodeCode())) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_NODE_REFERENCE_INVALID,
                        "Duplicate node code: " + node.getNodeCode() + ".",
                        node.getNodeCode(), null);
            }
        }
    }

    private void validateStartAndEnd(ValidationResult result, DefinitionGraphIndex graph) {
        if (graph.getStartNodes().size() != 1) {
            addIssue(result, FrozenValidationErrorCodes.MODEL_START_NODE_INVALID,
                    "Definition model must contain exactly one start node.", null, null);
        }
        if (graph.getEndNodes().isEmpty()) {
            addIssue(result, FrozenValidationErrorCodes.MODEL_END_NODE_REQUIRED,
                    "Definition model must contain at least one end node.", null, null);
        }
    }

    private void validateEdges(ValidationResult result, DefinitionGraphIndex graph) {
        Set<String> edgeCodes = new LinkedHashSet<String>();
        for (ProcessEdgeDTO edge : graph.getEdges()) {
            if (isBlank(edge.getEdgeCode())) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_EDGE_REFERENCE_INVALID,
                        "Edge code must not be blank.", null, null);
            } else if (!edgeCodes.add(edge.getEdgeCode())) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_EDGE_REFERENCE_INVALID,
                        "Duplicate edge code: " + edge.getEdgeCode() + ".",
                        null, edge.getEdgeCode());
            }
            if (!graph.getNodesByCode().containsKey(edge.getSourceNodeCode())) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_EDGE_REFERENCE_INVALID,
                        "Edge source node does not exist: " + edge.getSourceNodeCode() + ".",
                        edge.getSourceNodeCode(), edge.getEdgeCode());
            }
            if (!graph.getNodesByCode().containsKey(edge.getTargetNodeCode())) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_EDGE_REFERENCE_INVALID,
                        "Edge target node does not exist: " + edge.getTargetNodeCode() + ".",
                        edge.getTargetNodeCode(), edge.getEdgeCode());
            }
        }
    }

    private void validateSingleOutgoingNodes(ValidationResult result, DefinitionGraphIndex graph) {
        for (ProcessNodeDTO node : graph.getNodes()) {
            if ((!NodeTypeEnum.START.equals(node.getNodeType())
                    && !USER_TASK.equals(node.getNodeType()))
                    || isBlank(node.getNodeCode())) {
                continue;
            }
            if (countResolvableOutgoingEdges(node.getNodeCode(), graph) != 1) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_NODE_OUTGOING_EDGE_INVALID,
                        "Start node and user task must have exactly one outgoing edge to an existing node.",
                        node.getNodeCode(), null);
            }
        }
    }

    private void validateUserTasks(ValidationResult result, List<ProcessNodeDTO> nodes) {
        for (ProcessNodeDTO node : nodes) {
            if (USER_TASK.equals(node.getNodeType())
                    && (node.getApproverRuleType() == null
                    || (!STARTER.equals(node.getApproverRuleType())
                    && isBlank(node.getApproverRuleConfig())))) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_USER_TASK_APPROVER_REQUIRED,
                        "User task must define approver rule.", node.getNodeCode(), null);
            }
        }
    }

    private void validateGatewayNodeConfiguration(ValidationResult result, DefinitionGraphIndex graph) {
        for (ProcessNodeDTO node : graph.getGatewayNodes()) {
            if (node.getApproverRuleType() != null || !isBlank(node.getApproverRuleConfig())
                    || node.getMultiInstanceMode() != null) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_GATEWAY_NODE_CONFIGURATION_INVALID,
                        "Gateway node must not define approver or multi-instance configuration.",
                        node.getNodeCode(), null);
            }
        }
    }

    private void validateExclusiveGatewayTopology(ValidationResult result, DefinitionGraphIndex graph) {
        for (ProcessNodeDTO node : graph.getGatewayNodes()) {
            if (!EXCLUSIVE_GATEWAY.equals(node.getNodeType()) || isBlank(node.getNodeCode())) {
                continue;
            }
            List<ProcessEdgeDTO> outgoingEdges = graph.getOutgoingEdges(node.getNodeCode());
            if (outgoingEdges.size() < 2) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_EXCLUSIVE_GATEWAY_TOPOLOGY_INVALID,
                        "Exclusive gateway must have at least two outgoing edges.",
                        node.getNodeCode(), null);
            }
            int defaultCount = 0;
            for (ProcessEdgeDTO edge : outgoingEdges) {
                if (Boolean.TRUE.equals(edge.getDefaultEdge())) {
                    defaultCount++;
                } else if (isBlank(edge.getConditionExpression())) {
                    addIssue(result, FrozenValidationErrorCodes.MODEL_EXCLUSIVE_GATEWAY_TOPOLOGY_INVALID,
                            "Non-default exclusive gateway edge must define a condition expression.",
                            node.getNodeCode(), edge.getEdgeCode());
                }
            }
            if (defaultCount > 1) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_GATEWAY_DEFAULT_EDGE_INVALID,
                        "Exclusive gateway can have at most one default outgoing edge.",
                        node.getNodeCode(), null);
            }
        }
    }

    private void validateParallelGatewayTopology(ValidationResult result, DefinitionGraphIndex graph) {
        for (ProcessNodeDTO node : graph.getGatewayNodes()) {
            if ((!PARALLEL_SPLIT_GATEWAY.equals(node.getNodeType())
                    && !PARALLEL_JOIN_GATEWAY.equals(node.getNodeType()))
                    || isBlank(node.getNodeCode())) {
                continue;
            }
            ProcessNodeDTO paired = graph.getNodesByCode().get(node.getPairedGatewayCode());
            if (paired == null || !isExpectedPairType(node, paired)
                    || !node.getNodeCode().equals(paired.getPairedGatewayCode())) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_PARALLEL_GATEWAY_PAIR_INVALID,
                        "Parallel gateway pair is invalid.", node.getNodeCode(), null);
                continue;
            }
            if (PARALLEL_SPLIT_GATEWAY.equals(node.getNodeType())
                    ) {
                validateParallelSplitGateway(result, node, paired, graph);
            } else {
                validateParallelJoinGateway(result, node, graph);
            }
        }
    }

    private void validateParallelSplitGateway(ValidationResult result,
                                              ProcessNodeDTO split,
                                              ProcessNodeDTO join,
                                              DefinitionGraphIndex graph) {
        List<ProcessEdgeDTO> outgoingEdges = graph.getOutgoingEdges(split.getNodeCode());
        if (outgoingEdges.size() < 2) {
            addIssue(result, FrozenValidationErrorCodes.MODEL_PARALLEL_GATEWAY_TOPOLOGY_INVALID,
                    "Parallel split gateway must have at least two outgoing branches.",
                    split.getNodeCode(), null);
        }
        for (ProcessEdgeDTO edge : outgoingEdges) {
            if (!isBlank(edge.getConditionExpression())) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_PARALLEL_GATEWAY_TOPOLOGY_INVALID,
                        "Parallel split gateway edge must not define a condition expression.",
                        split.getNodeCode(), edge.getEdgeCode());
            }
            if (!allBranchPathsReachJoin(edge.getTargetNodeCode(), join.getNodeCode(), graph)) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_PARALLEL_GATEWAY_TOPOLOGY_INVALID,
                        "Parallel branch must reach its paired join gateway on every path.",
                        split.getNodeCode(), edge.getEdgeCode());
            }
        }
    }

    private void validateParallelJoinGateway(ValidationResult result,
                                             ProcessNodeDTO join,
                                             DefinitionGraphIndex graph) {
        if (graph.getIncomingEdges(join.getNodeCode()).size() < 2) {
            addIssue(result, FrozenValidationErrorCodes.MODEL_PARALLEL_GATEWAY_TOPOLOGY_INVALID,
                    "Parallel join gateway must have at least two incoming branches.",
                    join.getNodeCode(), null);
        }
        if (graph.getOutgoingEdges(join.getNodeCode()).size() != 1) {
            addIssue(result, FrozenValidationErrorCodes.MODEL_PARALLEL_GATEWAY_TOPOLOGY_INVALID,
                    "Parallel join gateway must have exactly one outgoing edge.",
                    join.getNodeCode(), null);
        }
    }

    private boolean allBranchPathsReachJoin(String branchStartCode,
                                            String joinCode,
                                            DefinitionGraphIndex graph) {
        if (isBlank(branchStartCode) || !graph.getNodesByCode().containsKey(branchStartCode)) {
            return false;
        }
        Set<String> reachableBeforeJoin = walkForwardUntil(branchStartCode, joinCode, graph);
        if (!reachableBeforeJoin.contains(joinCode)
                || !walkBackward(Collections.singleton(joinCode), graph)
                .containsAll(reachableBeforeJoin)) {
            return false;
        }
        return !hasCycleBeforeJoin(branchStartCode, joinCode, graph,
                new HashSet<String>(), new HashSet<String>());
    }

    private boolean hasCycleBeforeJoin(String currentCode,
                                       String joinCode,
                                       DefinitionGraphIndex graph,
                                       Set<String> activePath,
                                       Set<String> completed) {
        if (joinCode.equals(currentCode) || !graph.getNodesByCode().containsKey(currentCode)) {
            return false;
        }
        if (activePath.contains(currentCode)) {
            return true;
        }
        if (!completed.add(currentCode)) {
            return false;
        }
        activePath.add(currentCode);
        for (ProcessEdgeDTO edge : graph.getOutgoingEdges(currentCode)) {
            if (hasCycleBeforeJoin(edge.getTargetNodeCode(), joinCode, graph,
                    activePath, completed)) {
                return true;
            }
        }
        activePath.remove(currentCode);
        return false;
    }

    private void validateFiniteEnding(ValidationResult result, DefinitionGraphIndex graph) {
        Set<String> activePath = new HashSet<String>();
        Set<String> completed = new HashSet<String>();
        for (ProcessNodeDTO node : graph.getNodes()) {
            if (!isBlank(node.getNodeCode())
                    && hasDirectedCycle(node.getNodeCode(), graph, activePath, completed)) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_GRAPH_CYCLE_INVALID,
                        "Definition graph must not contain a directed cycle.", null, null);
                return;
            }
        }
    }

    private boolean hasDirectedCycle(String currentCode,
                                     DefinitionGraphIndex graph,
                                     Set<String> activePath,
                                     Set<String> completed) {
        if (!graph.getNodesByCode().containsKey(currentCode)) {
            return false;
        }
        if (activePath.contains(currentCode)) {
            return true;
        }
        if (!completed.add(currentCode)) {
            return false;
        }
        activePath.add(currentCode);
        for (ProcessEdgeDTO edge : graph.getOutgoingEdges(currentCode)) {
            if (hasDirectedCycle(edge.getTargetNodeCode(), graph, activePath, completed)) {
                return true;
            }
        }
        activePath.remove(currentCode);
        return false;
    }

    private int countResolvableOutgoingEdges(String nodeCode, DefinitionGraphIndex graph) {
        int count = 0;
        for (ProcessEdgeDTO edge : graph.getOutgoingEdges(nodeCode)) {
            if (graph.getNodesByCode().containsKey(edge.getTargetNodeCode())) {
                count++;
            }
        }
        return count;
    }

    private void validateExtensionConfiguration(ValidationResult result,
                                                ProcessDefinitionDetailDTO definition,
                                                DefinitionGraphIndex graph) {
        validateFormFields(result, definition.getFormFields());
        validateAttachmentTemplates(result, definition.getAttachmentTemplates(), graph);
    }

    private void validateFormFields(ValidationResult result, List<ProcessFormFieldDTO> formFields) {
        Set<String> fieldCodes = new LinkedHashSet<String>();
        if (formFields == null) {
            return;
        }
        for (ProcessFormFieldDTO formField : formFields) {
            if (formField == null) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_FORM_FIELD_INVALID,
                        "Definition model contains a null form field.", null, null);
            }
        }
        for (ProcessFormFieldDTO formField : sortedFormFields(formFields)) {
            validateFormFieldCode(result, formField, fieldCodes);
        }
    }

    private void validateFormFieldCode(ValidationResult result,
                                       ProcessFormFieldDTO formField,
                                       Set<String> fieldCodes) {
        if (!fieldCodes.add(formField.getFieldCode())) {
            addIssue(result, FrozenValidationErrorCodes.MODEL_FORM_FIELD_INVALID,
                    "Duplicate form field code: " + formField.getFieldCode() + ".", null, null);
        }
    }

    private void validateAttachmentTemplates(ValidationResult result,
                                             List<ProcessAttachmentTemplateDTO> attachmentTemplates,
                                             DefinitionGraphIndex graph) {
        Map<String, Set<String>> templateIdsByConfig = new LinkedHashMap<String, Set<String>>();
        Map<String, Set<String>> attachmentCodesByConfig = new LinkedHashMap<String, Set<String>>();
        if (attachmentTemplates == null) {
            return;
        }
        for (ProcessAttachmentTemplateDTO attachmentTemplate : attachmentTemplates) {
            if (attachmentTemplate == null) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_ATTACHMENT_CONFIGURATION_INVALID,
                        "Definition model contains a null attachment configuration.", null, null);
            }
        }
        for (ProcessAttachmentTemplateDTO attachmentTemplate : sortedAttachmentTemplates(attachmentTemplates)) {
            String configId = attachmentTemplate.getAttachmentConfigId();
            if (!valuesFor(templateIdsByConfig, configId).add(attachmentTemplate.getAttachmentTemplateId())) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_ATTACHMENT_CONFIGURATION_INVALID,
                        "Duplicate attachment template ID in config: " + configId + ".", null, null);
            }
            if (!valuesFor(attachmentCodesByConfig, configId).add(attachmentTemplate.getAttachmentCode())) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_ATTACHMENT_CONFIGURATION_INVALID,
                        "Duplicate attachment code in config: " + configId + ".", null, null);
            }
            validateApplicableNodes(result, attachmentTemplate, graph);
            validateAttachmentCounts(result, attachmentTemplate);
        }
    }

    private void validateApplicableNodes(ValidationResult result,
                                         ProcessAttachmentTemplateDTO attachmentTemplate,
                                         DefinitionGraphIndex graph) {
        if (attachmentTemplate.getApplicableNodeCodes() == null) {
            return;
        }
        for (String nodeCode : attachmentTemplate.getApplicableNodeCodes()) {
            if (!graph.getNodesByCode().containsKey(nodeCode)) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_ATTACHMENT_CONFIGURATION_INVALID,
                        "Attachment applicable node does not exist: " + nodeCode + ".",
                        nodeCode, null);
            }
        }
    }

    private void validateAttachmentCounts(ValidationResult result,
                                          ProcessAttachmentTemplateDTO attachmentTemplate) {
        Integer minCount = attachmentTemplate.getMinCount();
        Integer maxCount = attachmentTemplate.getMaxCount();
        if (Boolean.TRUE.equals(attachmentTemplate.getRequired())
                && (minCount == null || minCount.intValue() < 1)) {
            addIssue(result, FrozenValidationErrorCodes.MODEL_ATTACHMENT_CONFIGURATION_INVALID,
                    "Required attachment must define minCount of at least one.", null, null);
        }
        if (minCount != null && maxCount != null && minCount.compareTo(maxCount) > 0) {
            addIssue(result, FrozenValidationErrorCodes.MODEL_ATTACHMENT_CONFIGURATION_INVALID,
                    "Attachment minCount must not exceed maxCount.", null, null);
        }
    }

    private Set<String> valuesFor(Map<String, Set<String>> valuesByConfig, String configId) {
        Set<String> values = valuesByConfig.get(configId);
        if (values == null) {
            values = new LinkedHashSet<String>();
            valuesByConfig.put(configId, values);
        }
        return values;
    }

    private List<ProcessFormFieldDTO> sortedFormFields(List<ProcessFormFieldDTO> formFields) {
        List<ProcessFormFieldDTO> ordered = new ArrayList<ProcessFormFieldDTO>();
        if (formFields != null) {
            for (ProcessFormFieldDTO formField : formFields) {
                if (formField != null) {
                    ordered.add(formField);
                }
            }
        }
        Collections.sort(ordered, FORM_FIELD_ORDER);
        return ordered;
    }

    private List<ProcessAttachmentTemplateDTO> sortedAttachmentTemplates(
            List<ProcessAttachmentTemplateDTO> attachmentTemplates) {
        List<ProcessAttachmentTemplateDTO> ordered = new ArrayList<ProcessAttachmentTemplateDTO>();
        if (attachmentTemplates != null) {
            for (ProcessAttachmentTemplateDTO attachmentTemplate : attachmentTemplates) {
                if (attachmentTemplate != null) {
                    ordered.add(attachmentTemplate);
                }
            }
        }
        Collections.sort(ordered, ATTACHMENT_TEMPLATE_ORDER);
        return ordered;
    }

    private void validateReachability(ValidationResult result, DefinitionGraphIndex graph) {
        ProcessNodeDTO start = firstNodeWithCode(graph.getStartNodes());
        Set<String> endCodes = nodeCodes(graph.getEndNodes());
        if (start == null || endCodes.isEmpty()) {
            return;
        }
        Set<String> reachableFromStart = walkForward(start.getNodeCode(), graph);
        Set<String> canReachEnd = walkBackward(endCodes, graph);
        for (ProcessNodeDTO node : graph.getNodes()) {
            if (isBlank(node.getNodeCode())) {
                continue;
            }
            if (!reachableFromStart.contains(node.getNodeCode())
                    || !canReachEnd.contains(node.getNodeCode())) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_ORPHAN_NODE_INVALID,
                        "Node is isolated from start or end path.", node.getNodeCode(), null);
            }
        }
    }

    private ProcessNodeDTO firstNodeWithCode(List<ProcessNodeDTO> nodes) {
        for (ProcessNodeDTO node : nodes) {
            if (!isBlank(node.getNodeCode())) {
                return node;
            }
        }
        return null;
    }

    private Set<String> nodeCodes(List<ProcessNodeDTO> nodes) {
        Set<String> nodeCodes = new LinkedHashSet<String>();
        for (ProcessNodeDTO node : nodes) {
            if (!isBlank(node.getNodeCode())) {
                nodeCodes.add(node.getNodeCode());
            }
        }
        return nodeCodes;
    }

    private Set<String> walkForward(String startCode, DefinitionGraphIndex graph) {
        Set<String> visited = new HashSet<String>();
        Queue<String> queue = new ArrayDeque<String>();
        queue.add(startCode);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (!visited.add(current)) {
                continue;
            }
            for (ProcessEdgeDTO edge : graph.getOutgoingEdges(current)) {
                if (!isBlank(edge.getTargetNodeCode())) {
                    queue.add(edge.getTargetNodeCode());
                }
            }
        }
        return visited;
    }

    private Set<String> walkForwardUntil(String startCode,
                                         String terminalCode,
                                         DefinitionGraphIndex graph) {
        Set<String> visited = new HashSet<String>();
        Queue<String> queue = new ArrayDeque<String>();
        queue.add(startCode);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (!visited.add(current) || terminalCode.equals(current)) {
                continue;
            }
            for (ProcessEdgeDTO edge : graph.getOutgoingEdges(current)) {
                if (!isBlank(edge.getTargetNodeCode())) {
                    queue.add(edge.getTargetNodeCode());
                }
            }
        }
        return visited;
    }

    private Set<String> walkBackward(Set<String> endCodes, DefinitionGraphIndex graph) {
        Set<String> visited = new HashSet<String>();
        Queue<String> queue = new ArrayDeque<String>();
        queue.addAll(endCodes);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (!visited.add(current)) {
                continue;
            }
            for (ProcessEdgeDTO edge : graph.getIncomingEdges(current)) {
                if (!isBlank(edge.getSourceNodeCode())) {
                    queue.add(edge.getSourceNodeCode());
                }
            }
        }
        return visited;
    }

    private boolean isExpectedPairType(ProcessNodeDTO node, ProcessNodeDTO paired) {
        return (PARALLEL_SPLIT_GATEWAY.equals(node.getNodeType())
                && PARALLEL_JOIN_GATEWAY.equals(paired.getNodeType()))
                || (PARALLEL_JOIN_GATEWAY.equals(node.getNodeType())
                && PARALLEL_SPLIT_GATEWAY.equals(paired.getNodeType()));
    }

    private static int compareNullableInteger(Integer left, Integer right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    private static int compareNullableString(String left, String right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    private void addIssue(ValidationResult result,
                          String code,
                          String message,
                          String nodeCode,
                          String edgeCode) {
        ValidationResult.Issue issue = new ValidationResult.Issue();
        issue.setCode(code);
        issue.setMessage(message);
        issue.setNodeCode(nodeCode);
        issue.setEdgeCode(edgeCode);
        result.getIssues().add(issue);
        result.setValid(false);
    }

    private void validateNodeType(String value) {
        try {
            NodeTypeEnum.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new DefinitionValidationException(DefinitionErrorCodes.DEFINITION_INVALID,
                    "nodeType is invalid: " + value, ex);
        }
    }

    private void validateApproverRuleType(String value) {
        try {
            ApproverRuleTypeEnum.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new DefinitionValidationException(DefinitionErrorCodes.DEFINITION_INVALID,
                    "approverRuleType is invalid: " + value, ex);
        }
    }

    private void validateMultiInstanceMode(String value) {
        try {
            MultiInstanceModeEnum.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new DefinitionValidationException(DefinitionErrorCodes.DEFINITION_INVALID,
                    "multiInstanceMode is invalid: " + value, ex);
        }
    }

    private void validateRequiredText(String value, String fieldName) {
        if (isBlank(value)) {
            throw new DefinitionValidationException(DefinitionErrorCodes.DEFINITION_INVALID,
                    fieldName + " must not be empty");
        }
    }

    private String describeIssues(ValidationResult result) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < result.getIssues().size(); i++) {
            if (i > 0) {
                builder.append("; ");
            }
            ValidationResult.Issue issue = result.getIssues().get(i);
            builder.append(issue.getCode()).append('[')
                    .append(safe(issue.getNodeCode())).append(',')
                    .append(safe(issue.getEdgeCode())).append("] ")
                    .append(issue.getMessage());
        }
        return builder.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
