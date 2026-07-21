package com.flowmind.platform.core.validation;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.enums.DefinitionErrorCodes;
import com.flowmind.platform.core.definition.DefinitionValidationException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
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
 * <p>M0.5 只做发布前的结构约束表达，不在这里执行发布、部署或运行时推进。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 */
public class DefinitionModelValidator {

    private static final String START = "START";
    private static final String END = "END";
    private static final String USER_TASK = "USER_TASK";
    private static final String EXCLUSIVE_GATEWAY = "EXCLUSIVE_GATEWAY";
    private static final String PARALLEL_SPLIT_GATEWAY = "PARALLEL_SPLIT_GATEWAY";
    private static final String PARALLEL_JOIN_GATEWAY = "PARALLEL_JOIN_GATEWAY";
    private static final String STARTER = "STARTER";

    public ValidationResult validate(ProcessDefinitionDetailDTO definition) {
        ValidationResult result = new ValidationResult();
        result.setValid(true);
        if (definition == null) {
            addIssue(result, FrozenValidationErrorCodes.MODEL_START_NODE_INVALID,
                    "Definition model must not be null.", null, null);
            return result;
        }

        List<ProcessNodeDTO> nodes = safeNodes(definition.getNodes());
        List<ProcessEdgeDTO> edges = safeEdges(definition.getEdges());
        Map<String, ProcessNodeDTO> nodeByCode = indexNodes(result, nodes);
        Map<String, List<ProcessEdgeDTO>> outgoing = new HashMap<String, List<ProcessEdgeDTO>>();
        Map<String, List<ProcessEdgeDTO>> incoming = new HashMap<String, List<ProcessEdgeDTO>>();

        // 各校验步骤独立追加 issue，尽量一次返回完整模型问题列表。
        validateStartAndEnd(result, nodes);
        validateEdges(result, edges, nodeByCode, outgoing, incoming);
        validateUserTasks(result, nodes);
        validateExclusiveGatewayDefaults(result, nodes, outgoing);
        validateParallelGatewayPairs(result, nodes, nodeByCode, outgoing, incoming);
        validateReachability(result, nodes, edges, outgoing, incoming);
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
        for (ProcessNodeDTO node : nodes) {
            // node_code 是连线引用和网关配对的基础标识，不能为空且不能重复。
            if (isBlank(node.getNodeCode())) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_NODE_REFERENCE_INVALID,
                        "Node code must not be blank.", null, null);
                continue;
            }
            ProcessNodeDTO previous = nodeByCode.put(node.getNodeCode(), node);
            if (previous != null) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_NODE_REFERENCE_INVALID,
                        "Duplicate node code: " + node.getNodeCode() + ".",
                        node.getNodeCode(), null);
            }
        }
        return nodeByCode;
    }

    private void validateStartAndEnd(ValidationResult result, List<ProcessNodeDTO> nodes) {
        int startCount = 0;
        int endCount = 0;
        for (ProcessNodeDTO node : nodes) {
            if (START.equals(node.getNodeType())) {
                startCount++;
            }
            if (END.equals(node.getNodeType())) {
                endCount++;
            }
        }
        if (startCount != 1) {
            addIssue(result, FrozenValidationErrorCodes.MODEL_START_NODE_INVALID,
                    "Definition model must contain exactly one start node.", null, null);
        }
        if (endCount < 1) {
            addIssue(result, FrozenValidationErrorCodes.MODEL_END_NODE_REQUIRED,
                    "Definition model must contain at least one end node.", null, null);
        }
    }

    private void validateEdges(ValidationResult result,
                               List<ProcessEdgeDTO> edges,
                               Map<String, ProcessNodeDTO> nodeByCode,
                               Map<String, List<ProcessEdgeDTO>> outgoing,
                               Map<String, List<ProcessEdgeDTO>> incoming) {
        Set<String> edgeCodes = new LinkedHashSet<String>();
        for (ProcessEdgeDTO edge : edges) {
            // 连线编码用于分支键和审计定位，重复会导致运行时语义不确定。
            if (!isBlank(edge.getEdgeCode()) && !edgeCodes.add(edge.getEdgeCode())) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_EDGE_REFERENCE_INVALID,
                        "Duplicate edge code: " + edge.getEdgeCode() + ".",
                        null, edge.getEdgeCode());
            }
            // 连线两端必须都能解析到当前定义内的节点。
            if (!nodeByCode.containsKey(edge.getSourceNodeCode())) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_EDGE_REFERENCE_INVALID,
                        "Edge source node does not exist: " + edge.getSourceNodeCode() + ".",
                        edge.getSourceNodeCode(), edge.getEdgeCode());
            } else {
                putEdge(outgoing, edge.getSourceNodeCode(), edge);
            }
            if (!nodeByCode.containsKey(edge.getTargetNodeCode())) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_EDGE_REFERENCE_INVALID,
                        "Edge target node does not exist: " + edge.getTargetNodeCode() + ".",
                        edge.getTargetNodeCode(), edge.getEdgeCode());
            } else {
                putEdge(incoming, edge.getTargetNodeCode(), edge);
            }
        }
    }

    private void validateUserTasks(ValidationResult result, List<ProcessNodeDTO> nodes) {
        for (ProcessNodeDTO node : nodes) {
            // 用户任务必须有审批人规则；STARTER 规则不强制额外配置。
            if (USER_TASK.equals(node.getNodeType())
                    && (isBlank(node.getApproverRuleType())
                    || (!STARTER.equals(node.getApproverRuleType())
                    && isBlank(node.getApproverRuleConfig())))) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_USER_TASK_APPROVER_REQUIRED,
                        "User task must define approver rule.",
                        node.getNodeCode(), null);
            }
        }
    }

    private void validateExclusiveGatewayDefaults(ValidationResult result,
                                                  List<ProcessNodeDTO> nodes,
                                                  Map<String, List<ProcessEdgeDTO>> outgoing) {
        for (ProcessNodeDTO node : nodes) {
            if (!EXCLUSIVE_GATEWAY.equals(node.getNodeType())) {
                continue;
            }
            // 条件网关允许没有默认线，但最多只能有一条默认线。
            int defaultCount = 0;
            for (ProcessEdgeDTO edge : edgesOf(outgoing, node.getNodeCode())) {
                if (Boolean.TRUE.equals(edge.getDefaultEdge())) {
                    defaultCount++;
                }
            }
            if (defaultCount > 1) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_GATEWAY_DEFAULT_EDGE_INVALID,
                        "Exclusive gateway can have at most one default outgoing edge.",
                        node.getNodeCode(), null);
            }
        }
    }

    private void validateParallelGatewayPairs(ValidationResult result,
                                              List<ProcessNodeDTO> nodes,
                                              Map<String, ProcessNodeDTO> nodeByCode,
                                              Map<String, List<ProcessEdgeDTO>> outgoing,
                                              Map<String, List<ProcessEdgeDTO>> incoming) {
        for (ProcessNodeDTO node : nodes) {
            if (!PARALLEL_SPLIT_GATEWAY.equals(node.getNodeType())
                    && !PARALLEL_JOIN_GATEWAY.equals(node.getNodeType())) {
                continue;
            }
            // 并行拆分和汇聚必须互相声明配对，避免跨网关误汇聚。
            ProcessNodeDTO paired = nodeByCode.get(node.getPairedGatewayCode());
            if (paired == null || !isExpectedPairType(node, paired)
                    || !node.getNodeCode().equals(paired.getPairedGatewayCode())) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_PARALLEL_GATEWAY_PAIR_INVALID,
                        "Parallel gateway pair is invalid.",
                        node.getNodeCode(), null);
                continue;
            }
            // 拆分至少两个出分支，汇聚至少两个入分支，才具备并行语义。
            if (PARALLEL_SPLIT_GATEWAY.equals(node.getNodeType())
                    && edgesOf(outgoing, node.getNodeCode()).size() < 2) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_PARALLEL_GATEWAY_PAIR_INVALID,
                        "Parallel split gateway must have at least two outgoing branches.",
                        node.getNodeCode(), null);
            }
            if (PARALLEL_JOIN_GATEWAY.equals(node.getNodeType())
                    && edgesOf(incoming, node.getNodeCode()).size() < 2) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_PARALLEL_GATEWAY_PAIR_INVALID,
                        "Parallel join gateway must have at least two incoming branches.",
                        node.getNodeCode(), null);
            }
        }
    }

    private void validateReachability(ValidationResult result,
                                      List<ProcessNodeDTO> nodes,
                                      List<ProcessEdgeDTO> edges,
                                      Map<String, List<ProcessEdgeDTO>> outgoing,
                                      Map<String, List<ProcessEdgeDTO>> incoming) {
        ProcessNodeDTO start = null;
        Set<String> endCodes = new LinkedHashSet<String>();
        for (ProcessNodeDTO node : nodes) {
            if (START.equals(node.getNodeType())) {
                start = node;
            }
            if (END.equals(node.getNodeType())) {
                endCodes.add(node.getNodeCode());
            }
        }
        if (start == null || endCodes.isEmpty()) {
            return;
        }
        // 节点必须既能从开始节点到达，也能继续到达某个结束节点。
        Set<String> reachableFromStart = walkForward(start.getNodeCode(), outgoing);
        Set<String> canReachEnd = walkBackward(endCodes, incoming);
        for (ProcessNodeDTO node : nodes) {
            String nodeCode = node.getNodeCode();
            if (!reachableFromStart.contains(nodeCode) || !canReachEnd.contains(nodeCode)) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_ORPHAN_NODE_INVALID,
                        "Node is isolated from start or end path.",
                        nodeCode, null);
            }
        }
    }

    private Set<String> walkForward(String startCode, Map<String, List<ProcessEdgeDTO>> outgoing) {
        Set<String> visited = new HashSet<String>();
        Queue<String> queue = new ArrayDeque<String>();
        queue.add(startCode);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (!visited.add(current)) {
                continue;
            }
            for (ProcessEdgeDTO edge : edgesOf(outgoing, current)) {
                queue.add(edge.getTargetNodeCode());
            }
        }
        return visited;
    }

    private Set<String> walkBackward(Set<String> endCodes, Map<String, List<ProcessEdgeDTO>> incoming) {
        Set<String> visited = new HashSet<String>();
        Queue<String> queue = new ArrayDeque<String>();
        queue.addAll(endCodes);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (!visited.add(current)) {
                continue;
            }
            for (ProcessEdgeDTO edge : edgesOf(incoming, current)) {
                queue.add(edge.getSourceNodeCode());
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

    private List<ProcessNodeDTO> safeNodes(List<ProcessNodeDTO> nodes) {
        return nodes == null ? new ArrayList<ProcessNodeDTO>() : nodes;
    }

    private List<ProcessEdgeDTO> safeEdges(List<ProcessEdgeDTO> edges) {
        return edges == null ? new ArrayList<ProcessEdgeDTO>() : edges;
    }

    private void putEdge(Map<String, List<ProcessEdgeDTO>> edgesByNode,
                         String nodeCode,
                         ProcessEdgeDTO edge) {
        List<ProcessEdgeDTO> edges = edgesByNode.get(nodeCode);
        if (edges == null) {
            edges = new ArrayList<ProcessEdgeDTO>();
            edgesByNode.put(nodeCode, edges);
        }
        edges.add(edge);
    }

    private List<ProcessEdgeDTO> edgesOf(Map<String, List<ProcessEdgeDTO>> edgesByNode,
                                         String nodeCode) {
        List<ProcessEdgeDTO> edges = edgesByNode.get(nodeCode);
        return edges == null ? new ArrayList<ProcessEdgeDTO>() : edges;
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
