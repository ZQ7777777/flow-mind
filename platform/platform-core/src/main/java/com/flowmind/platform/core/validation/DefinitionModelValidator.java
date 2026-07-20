package com.flowmind.platform.core.validation;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
        validateUserTasks(result, graph.getNodes());
        validateExclusiveGatewayDefaults(result, graph);
        validateParallelGatewayPairs(result, graph);
        validateReachability(result, graph);
        return result;
    }

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
            if (!isBlank(edge.getEdgeCode()) && !edgeCodes.add(edge.getEdgeCode())) {
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

    private void validateExclusiveGatewayDefaults(ValidationResult result, DefinitionGraphIndex graph) {
        for (ProcessNodeDTO node : graph.getGatewayNodes()) {
            if (!EXCLUSIVE_GATEWAY.equals(node.getNodeType())) {
                continue;
            }
            int defaultCount = 0;
            for (ProcessEdgeDTO edge : graph.getOutgoingEdges(node.getNodeCode())) {
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

    private void validateParallelGatewayPairs(ValidationResult result, DefinitionGraphIndex graph) {
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
                    && graph.getOutgoingEdges(node.getNodeCode()).size() < 2) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_PARALLEL_GATEWAY_PAIR_INVALID,
                        "Parallel split gateway must have at least two outgoing branches.",
                        node.getNodeCode(), null);
            }
            if (PARALLEL_JOIN_GATEWAY.equals(node.getNodeType())
                    && graph.getIncomingEdges(node.getNodeCode()).size() < 2) {
                addIssue(result, FrozenValidationErrorCodes.MODEL_PARALLEL_GATEWAY_PAIR_INVALID,
                        "Parallel join gateway must have at least two incoming branches.",
                        node.getNodeCode(), null);
            }
        }
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
