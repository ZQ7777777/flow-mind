package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.enums.BranchStatusEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.enums.TaskGroupTypeEnum;
import com.flowmind.platform.api.enums.TaskStatusEnum;
import com.flowmind.platform.api.spi.ApproverResolver;
import com.flowmind.platform.api.spi.ConditionExpressionEvaluator;
import com.flowmind.platform.core.validation.DefinitionGraphIndex;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.ProcessTaskGroupEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.TaskGroupRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 统一的运行时节点推进器。
 *
 * <p>启动、提交和审批动作均应通过 {@link #advanceToNode(ProcessInstanceEntity,
 * ProcessDefinitionDetailDTO, String, String, String)} 进入本类，避免各入口分别实现网关路由、
 * 并行汇聚或用户任务创建。调用方负责将整个动作置于一个数据库事务中。</p>
 *
 * @author FlowMind
 * @since 2026-07-22
 */
@Component
public class RuntimeNodeAdvancer {

    /** 任务组乐观锁冲突的最大重试次数。 */
    private static final int MAX_GROUP_UPDATE_ATTEMPTS = 3;
    /** 活动任务组的持久化状态值。 */
    private static final String TASK_GROUP_ACTIVE = "ACTIVE";
    /** 已完成任务组的持久化状态值。 */
    private static final String TASK_GROUP_COMPLETED = "COMPLETED";

    private final ActiveTaskRepository activeTaskRepository;
    private final TaskGroupRepository taskGroupRepository;
    private final ProcessInstanceRepository instanceRepository;
    private final RuntimeRequestValidator requestValidator;
    private final ApproverResolver approverResolver;
    private final ConditionExpressionEvaluator conditionExpressionEvaluator;
    private final ApproverResolveRequestFactory approverResolveRequestFactory;

    /** 创建统一节点推进器。 */
    public RuntimeNodeAdvancer(ActiveTaskRepository activeTaskRepository,
                               TaskGroupRepository taskGroupRepository,
                               ProcessInstanceRepository instanceRepository,
                               RuntimeRequestValidator requestValidator,
                               ApproverResolver approverResolver,
                               ConditionExpressionEvaluator conditionExpressionEvaluator,
                               ApproverResolveRequestFactory approverResolveRequestFactory) {
        this.activeTaskRepository = activeTaskRepository;
        this.taskGroupRepository = taskGroupRepository;
        this.instanceRepository = instanceRepository;
        this.requestValidator = requestValidator;
        this.approverResolver = approverResolver;
        this.conditionExpressionEvaluator = conditionExpressionEvaluator;
        this.approverResolveRequestFactory = approverResolveRequestFactory;
    }

    /**
     * 从目标节点开始推进，直到创建用户任务、等待并行汇聚或办结实例。
     *
     * @param instance       当前运行实例快照
     * @param definition     与实例冻结定义 ID 对应的完整定义
     * @param targetNodeCode 待进入的目标节点编码
     * @param taskGroupId    并行分支上下文中的任务组 ID；非并行路径为空
     * @param branchKey      并行分支上下文中的出线编码；非并行路径为空
     * @return 本次推进产生的活动任务和办结标识
     */
    public RuntimeAdvanceResult advanceToNode(ProcessInstanceEntity instance,
                                              ProcessDefinitionDetailDTO definition,
                                              String targetNodeCode,
                                              String taskGroupId,
                                              String branchKey) {
        return advanceToNode(instance, definition, targetNodeCode, taskGroupId, branchKey,
                prepareAdvance(instance, definition, targetNodeCode, taskGroupId, branchKey));
    }

    /**
     * Resolves all user-task candidates reachable by this advancement before
     * any runtime row is changed.  The result is passed back to
     * {@link #advanceToNode(ProcessInstanceEntity, ProcessDefinitionDetailDTO, String, String, String,
     * RuntimeAdvancePreparation)} so the mutation phase does not call the
     * external approver SPI.
     */
    public RuntimeAdvancePreparation prepareAdvance(ProcessInstanceEntity instance,
                                                    ProcessDefinitionDetailDTO definition,
                                                    String targetNodeCode,
                                                    String taskGroupId,
                                                    String branchKey) {
        assertInput(instance, definition, targetNodeCode, taskGroupId, branchKey);
        DefinitionGraphIndex graph = DefinitionGraphIndex.from(definition);
        Map<String, List<String>> candidatesByNodeCode = new LinkedHashMap<String, List<String>>();
        Map<String, String> exclusiveTargetNodeCodes = new LinkedHashMap<String, String>();
        prepareAdvance(instance, definition, graph, targetNodeCode,
                new AdvancePathContext(maxAutomaticSteps(graph)), candidatesByNodeCode, exclusiveTargetNodeCodes);
        return new RuntimeAdvancePreparation(candidatesByNodeCode, exclusiveTargetNodeCodes);
    }

    /**
     * Persists a previously prepared advancement.  This overload is used by
     * task actions after their task CAS has succeeded.
     */
    public RuntimeAdvanceResult advanceToNode(ProcessInstanceEntity instance,
                                              ProcessDefinitionDetailDTO definition,
                                              String targetNodeCode,
                                              String taskGroupId,
                                              String branchKey,
                                              RuntimeAdvancePreparation preparation) {
        assertInput(instance, definition, targetNodeCode, taskGroupId, branchKey);
        if (preparation == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                    "runtime advancement must be prepared before persistence");
        }
        DefinitionGraphIndex graph = DefinitionGraphIndex.from(definition);
        RuntimeAdvanceResult result = new RuntimeAdvanceResult();
        advance(instance, definition, graph, targetNodeCode, taskGroupId, branchKey,
                new AdvancePathContext(maxAutomaticSteps(graph)), preparation, result);
        if (!result.isInstanceCompleted()) {
            refreshCurrentNodeCodes(instance.getId());
        }
        return result;
    }

    private void advance(ProcessInstanceEntity instance,
                         ProcessDefinitionDetailDTO definition,
                         DefinitionGraphIndex graph,
                         String targetNodeCode,
                         String taskGroupId,
                         String branchKey,
                         AdvancePathContext path,
                         RuntimeAdvancePreparation preparation,
                         RuntimeAdvanceResult result) {
        ProcessNodeDTO node = graph.getNodesByCode().get(targetNodeCode);
        if (node == null) {
            throw state(RuntimeErrorCodes.NODE_NOT_FOUND, "target node does not exist: " + targetNodeCode);
        }
        if (node.getNodeType() == null) {
            throw state(RuntimeErrorCodes.DEFINITION_INVALID,
                    "target node type is missing: " + targetNodeCode);
        }
        switch (node.getNodeType()) {
            case USER_TASK:
                createUserTask(instance, node, taskGroupId, branchKey, preparation, result);
                return;
            case EXCLUSIVE_GATEWAY:
                path.enterAutomaticNode(node.getNodeCode());
                String selectedTargetNodeCode = preparation.exclusiveTargetNodeCode(node.getNodeCode());
                if (!hasText(selectedTargetNodeCode)) {
                    throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                            "prepared exclusive gateway target is missing: " + node.getNodeCode());
                }
                advance(instance, definition, graph, selectedTargetNodeCode, taskGroupId, branchKey, path,
                        preparation, result);
                return;
            case PARALLEL_SPLIT_GATEWAY:
                path.enterAutomaticNode(node.getNodeCode());
                advanceParallelSplit(instance, definition, node, graph, taskGroupId, branchKey, path, preparation, result);
                return;
            case PARALLEL_JOIN_GATEWAY:
                path.enterAutomaticNode(node.getNodeCode());
                advanceParallelJoin(instance, definition, node, graph, taskGroupId, branchKey, path, preparation, result);
                return;
            case END:
                path.enterAutomaticNode(node.getNodeCode());
                completeInstance(instance, result);
                return;
            case START:
            default:
                throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                        "START node cannot be entered by runtime advancement: " + targetNodeCode);
        }
    }

    private void createUserTask(ProcessInstanceEntity instance,
                                ProcessNodeDTO node,
                                String taskGroupId,
                                String branchKey,
                                RuntimeAdvancePreparation preparation,
                                RuntimeAdvanceResult result) {
        List<String> candidateUserIds = preparation.candidateUserIds(node.getNodeCode());
        if (candidateUserIds == null || candidateUserIds.isEmpty()) {
            throw state(RuntimeErrorCodes.APPROVER_RESOLVE_FAILED,
                    "prepared approvers are missing for user task: " + node.getNodeCode());
        }

        if (MultiInstanceModeEnum.COUNTERSIGN.equals(node.getMultiInstanceMode())) {
            createCountersignTasks(instance, node, taskGroupId, branchKey, candidateUserIds, result);
            return;
        }
        createActiveTask(instance, node, taskGroupId, branchKey, candidateUserIds, result);
    }

    private void createCountersignTasks(ProcessInstanceEntity instance,
                                        ProcessNodeDTO node,
                                        String parentGroupId,
                                        String parentBranchKey,
                                        List<String> candidateUserIds,
                                        RuntimeAdvanceResult result) {
        ProcessTaskGroupEntity group = new ProcessTaskGroupEntity();
        group.setId(newId());
        group.setInstanceId(instance.getId());
        group.setNodeCode(node.getNodeCode());
        group.setParentGroupId(parentGroupId);
        group.setParentBranchKey(parentBranchKey);
        group.setGroupType(TaskGroupTypeEnum.COUNTERSIGN.name());
        group.setTotalCount(Integer.valueOf(candidateUserIds.size()));
        group.setCompletedCount(Integer.valueOf(0));
        group.setBranchStateJson(RuntimeJsonCodec.toJson(new LinkedHashMap<String, Object>()));
        group.setGroupStatus(TASK_GROUP_ACTIVE);
        group.setLockVersion(Long.valueOf(0L));
        group.setCreatedAt(LocalDateTime.now());
        if (taskGroupRepository.insert(group) != 1) {
            throw state(RuntimeErrorCodes.INVALID_ACTION, "failed to create countersign task group");
        }
        for (String candidateUserId : candidateUserIds) {
            createActiveTask(instance, node, group.getId(), parentBranchKey,
                    java.util.Collections.singletonList(candidateUserId), result);
        }
    }

    private void createActiveTask(ProcessInstanceEntity instance,
                                  ProcessNodeDTO node,
                                  String taskGroupId,
                                  String branchKey,
                                  List<String> candidateUserIds,
                                  RuntimeAdvanceResult result) {
        ProcessActiveTaskEntity task = new ProcessActiveTaskEntity();
        task.setId(newId());
        task.setInstanceId(instance.getId());
        task.setDefinitionId(instance.getDefinitionId());
        task.setNodeCode(node.getNodeCode());
        task.setCandidateUserIds(RuntimeJsonCodec.toJson(candidateUserIds));
        task.setTaskStatus(TaskStatusEnum.ACTIVE.name());
        task.setTaskGroupId(taskGroupId);
        task.setBranchKey(branchKey);
        task.setLockVersion(Long.valueOf(0L));
        task.setCreatedAt(LocalDateTime.now());
        if (activeTaskRepository.insert(task) != 1) {
            throw state(RuntimeErrorCodes.INVALID_ACTION, "failed to create active task");
        }
        TaskDTO dto = RuntimeModelMapper.toDto(task, node.getNodeName(), null);
        result.addCreatedTask(dto);
    }

    private ProcessEdgeDTO selectExclusiveEdge(ProcessNodeDTO node,
                                                DefinitionGraphIndex graph,
                                                ProcessInstanceEntity instance) {
        List<ProcessEdgeDTO> outgoing = graph.getOutgoingEdges(node.getNodeCode());
        if (outgoing.size() < 2) {
            throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                    "exclusive gateway requires at least two outgoing edges: " + node.getNodeCode());
        }
        ProcessEdgeDTO defaultEdge = null;
        Map<String, Object> variables = readObjectMap(instance.getVariablesJson(), "instance variables");
        for (ProcessEdgeDTO edge : outgoing) {
            if (Boolean.TRUE.equals(edge.getDefaultEdge())) {
                if (defaultEdge != null) {
                    throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                            "exclusive gateway has multiple default edges: " + node.getNodeCode());
                }
                defaultEdge = edge;
                continue;
            }
            if (!hasText(edge.getConditionExpression())) {
                throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                        "exclusive gateway conditional edge has no expression: " + edge.getEdgeCode());
            }
            if (conditionExpressionEvaluator == null) {
                throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                        "condition expression evaluator is not configured");
            }
            try {
                if (conditionExpressionEvaluator.evaluate(edge.getConditionExpression(), variables)) {
                    return edge;
                }
            } catch (RuntimeException ex) {
                throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                        "condition expression evaluation failed for edge: " + edge.getEdgeCode());
            }
        }
        if (defaultEdge != null) {
            return defaultEdge;
        }
        throw state(RuntimeErrorCodes.GATEWAY_NO_MATCH,
                "exclusive gateway has no matching edge: " + node.getNodeCode());
    }

    private void advanceParallelSplit(ProcessInstanceEntity instance,
                                      ProcessDefinitionDetailDTO definition,
                                      ProcessNodeDTO node,
                                      DefinitionGraphIndex graph,
                                      String parentGroupId,
                                      String parentBranchKey,
                                      AdvancePathContext path,
                                      RuntimeAdvancePreparation preparation,
                                      RuntimeAdvanceResult result) {
        if (hasText(parentGroupId) || hasText(parentBranchKey)) {
            throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                    "nested parallel gateways are not supported in M2");
        }
        ProcessNodeDTO joinNode = assertParallelPair(node, graph);
        List<ProcessEdgeDTO> outgoing = graph.getOutgoingEdges(node.getNodeCode());
        if (outgoing.size() < 2) {
            throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                    "parallel split requires at least two outgoing edges: " + node.getNodeCode());
        }

        Map<String, Object> branches = new LinkedHashMap<String, Object>();
        for (ProcessEdgeDTO edge : outgoing) {
            if (!hasText(edge.getEdgeCode()) || hasText(edge.getConditionExpression())
                    || branches.put(edge.getEdgeCode(), BranchStatusEnum.RUNNING.name()) != null) {
                throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                        "parallel split has an invalid outgoing edge: " + node.getNodeCode());
            }
        }
        ProcessTaskGroupEntity group = new ProcessTaskGroupEntity();
        group.setId(newId());
        group.setInstanceId(instance.getId());
        group.setNodeCode(node.getNodeCode());
        group.setJoinNodeCode(joinNode.getNodeCode());
        group.setGroupType(TaskGroupTypeEnum.PARALLEL_GATEWAY.name());
        group.setTotalCount(Integer.valueOf(outgoing.size()));
        group.setCompletedCount(Integer.valueOf(0));
        group.setBranchStateJson(RuntimeJsonCodec.toJson(branches));
        group.setGroupStatus(TASK_GROUP_ACTIVE);
        group.setLockVersion(Long.valueOf(0L));
        group.setCreatedAt(LocalDateTime.now());
        if (taskGroupRepository.insert(group) != 1) {
            throw state(RuntimeErrorCodes.INVALID_ACTION, "failed to create parallel task group");
        }
        for (ProcessEdgeDTO edge : outgoing) {
            advance(instance, definition, graph, edge.getTargetNodeCode(), group.getId(), edge.getEdgeCode(),
                    path.copyForBranch(), preparation, result);
        }
    }

    private void advanceParallelJoin(ProcessInstanceEntity instance,
                                     ProcessDefinitionDetailDTO definition,
                                     ProcessNodeDTO node,
                                     DefinitionGraphIndex graph,
                                     String taskGroupId,
                                     String branchKey,
                                     AdvancePathContext path,
                                     RuntimeAdvancePreparation preparation,
                                     RuntimeAdvanceResult result) {
        if (!hasText(taskGroupId) || !hasText(branchKey)) {
            throw state(RuntimeErrorCodes.PARALLEL_JOIN_CONFLICT,
                    "parallel join requires taskGroupId and branchKey");
        }
        assertParallelPair(node, graph);
        ProcessTaskGroupEntity completedGroup = markParallelBranchArrived(instance, node, taskGroupId, branchKey);
        if (completedGroup == null) {
            return;
        }
        ProcessEdgeDTO outgoing = requireSingleOutgoing(node, graph);
        advance(instance, definition, graph, outgoing.getTargetNodeCode(), completedGroup.getParentGroupId(),
                completedGroup.getParentBranchKey(), path, preparation, result);
    }

    private void prepareAdvance(ProcessInstanceEntity instance,
                                ProcessDefinitionDetailDTO definition,
                                DefinitionGraphIndex graph,
                                String targetNodeCode,
                                AdvancePathContext path,
                                Map<String, List<String>> candidatesByNodeCode,
                                Map<String, String> exclusiveTargetNodeCodes) {
        ProcessNodeDTO node = graph.getNodesByCode().get(targetNodeCode);
        if (node == null) {
            throw state(RuntimeErrorCodes.NODE_NOT_FOUND, "target node does not exist: " + targetNodeCode);
        }
        if (node.getNodeType() == null) {
            throw state(RuntimeErrorCodes.DEFINITION_INVALID,
                    "target node type is missing: " + targetNodeCode);
        }
        switch (node.getNodeType()) {
            case USER_TASK:
                prepareUserTaskCandidates(instance, definition, node, candidatesByNodeCode);
                return;
            case EXCLUSIVE_GATEWAY:
                path.enterAutomaticNode(node.getNodeCode());
                ProcessEdgeDTO selected = selectExclusiveEdge(node, graph, instance);
                exclusiveTargetNodeCodes.put(node.getNodeCode(), selected.getTargetNodeCode());
                prepareAdvance(instance, definition, graph, selected.getTargetNodeCode(), path, candidatesByNodeCode,
                        exclusiveTargetNodeCodes);
                return;
            case PARALLEL_SPLIT_GATEWAY:
                path.enterAutomaticNode(node.getNodeCode());
                assertParallelPair(node, graph);
                List<ProcessEdgeDTO> outgoing = graph.getOutgoingEdges(node.getNodeCode());
                if (outgoing.size() < 2) {
                    throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                            "parallel split requires at least two outgoing edges: " + node.getNodeCode());
                }
                Set<String> branchKeys = new LinkedHashSet<String>();
                for (ProcessEdgeDTO edge : outgoing) {
                    if (!hasText(edge.getEdgeCode()) || hasText(edge.getConditionExpression())
                            || !branchKeys.add(edge.getEdgeCode())) {
                        throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                                "parallel split has an invalid outgoing edge: " + node.getNodeCode());
                    }
                    prepareAdvance(instance, definition, graph, edge.getTargetNodeCode(), path.copyForBranch(),
                            candidatesByNodeCode, exclusiveTargetNodeCodes);
                }
                return;
            case PARALLEL_JOIN_GATEWAY:
                path.enterAutomaticNode(node.getNodeCode());
                assertParallelPair(node, graph);
                prepareAdvance(instance, definition, graph, requireSingleOutgoing(node, graph).getTargetNodeCode(),
                        path, candidatesByNodeCode, exclusiveTargetNodeCodes);
                return;
            case END:
                path.enterAutomaticNode(node.getNodeCode());
                return;
            case START:
            default:
                throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                        "START node cannot be entered by runtime advancement: " + targetNodeCode);
        }
    }

    private void prepareUserTaskCandidates(ProcessInstanceEntity instance,
                                           ProcessDefinitionDetailDTO definition,
                                           ProcessNodeDTO node,
                                           Map<String, List<String>> candidatesByNodeCode) {
        if (candidatesByNodeCode.containsKey(node.getNodeCode())) {
            return;
        }
        Map<String, Object> variables = readObjectMap(instance.getVariablesJson(), "instance variables");
        if (approverResolveRequestFactory == null) {
            throw state(RuntimeErrorCodes.DEFINITION_INVALID,
                    "approver resolve request factory is not configured");
        }
        com.flowmind.platform.api.request.ApproverResolveRequest request = approverResolveRequestFactory.create(
                definition, instance.getId(), node.getNodeCode(), instance.getStarterUserId(),
                instance.getStarterDeptId(), variables);
        List<UserDTO> approvers = requestValidator.resolveApprovers(node.getMultiInstanceMode(), approverResolver, request);
        List<String> candidateUserIds = new ArrayList<String>();
        for (UserDTO approver : approvers) {
            candidateUserIds.add(approver.getUserId());
        }
        candidatesByNodeCode.put(node.getNodeCode(), candidateUserIds);
    }

    private ProcessTaskGroupEntity markParallelBranchArrived(ProcessInstanceEntity instance,
                                                              ProcessNodeDTO joinNode,
                                                              String taskGroupId,
                                                              String branchKey) {
        for (int attempt = 0; attempt < MAX_GROUP_UPDATE_ATTEMPTS; attempt++) {
            ProcessTaskGroupEntity group = taskGroupRepository.findById(taskGroupId);
            assertParallelGroup(instance, joinNode, branchKey, group);
            boolean completesGroup = group.getCompletedCount().intValue() + 1 == group.getTotalCount().intValue();
            if (taskGroupRepository.markBranchArrived(group.getId(), branchKey, group.getLockVersion().longValue()) == 1) {
                if (!completesGroup) {
                    return null;
                }
                ProcessTaskGroupEntity updated = taskGroupRepository.findById(taskGroupId);
                if (updated == null) {
                    throw state(RuntimeErrorCodes.PARALLEL_GROUP_NOT_FOUND,
                            "parallel task group disappeared: " + taskGroupId);
                }
                if (!TASK_GROUP_COMPLETED.equals(updated.getGroupStatus())) {
                    throw state(RuntimeErrorCodes.PARALLEL_JOIN_CONFLICT,
                            "last parallel branch did not complete its task group");
                }
                return updated;
            }
            ProcessTaskGroupEntity latest = taskGroupRepository.findById(taskGroupId);
            assertParallelGroup(instance, joinNode, branchKey, latest);
            if (!BranchStatusEnum.RUNNING.name().equals(readBranchStates(latest).get(branchKey))) {
                throw state(RuntimeErrorCodes.PARALLEL_JOIN_CONFLICT,
                        "parallel branch has already arrived: " + branchKey);
            }
        }
        throw state(RuntimeErrorCodes.TASK_GROUP_CONCURRENT_MODIFIED,
                "parallel task group update retry limit exceeded: " + taskGroupId);
    }

    private void assertParallelGroup(ProcessInstanceEntity instance,
                                     ProcessNodeDTO joinNode,
                                     String branchKey,
                                     ProcessTaskGroupEntity group) {
        if (group == null) {
            throw state(RuntimeErrorCodes.PARALLEL_GROUP_NOT_FOUND, "parallel task group does not exist");
        }
        if (!instance.getId().equals(group.getInstanceId())
                || !TaskGroupTypeEnum.PARALLEL_GATEWAY.name().equals(group.getGroupType())
                || !joinNode.getNodeCode().equals(group.getJoinNodeCode())) {
            throw state(RuntimeErrorCodes.PARALLEL_JOIN_CONFLICT,
                    "parallel task group does not match join node context");
        }
        if (group.getLockVersion() == null || group.getTotalCount() == null || group.getCompletedCount() == null
                || !TASK_GROUP_ACTIVE.equals(group.getGroupStatus())) {
            throw state(RuntimeErrorCodes.PARALLEL_JOIN_CONFLICT,
                    "parallel task group is not active");
        }
        if (group.getTotalCount().intValue() <= 0 || group.getCompletedCount().intValue() < 0
                || group.getCompletedCount().intValue() >= group.getTotalCount().intValue()) {
            throw state(RuntimeErrorCodes.PARALLEL_JOIN_CONFLICT,
                    "parallel task group counts are invalid");
        }
        if (!BranchStatusEnum.RUNNING.name().equals(readBranchStates(group).get(branchKey))) {
            throw state(RuntimeErrorCodes.PARALLEL_JOIN_CONFLICT,
                    "parallel branch is not running: " + branchKey);
        }
    }

    private ProcessNodeDTO assertParallelPair(ProcessNodeDTO node, DefinitionGraphIndex graph) {
        if (!hasText(node.getPairedGatewayCode())) {
            throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                    "parallel gateway pair is missing: " + node.getNodeCode());
        }
        ProcessNodeDTO paired = graph.getNodesByCode().get(node.getPairedGatewayCode());
        boolean splitToJoin = NodeTypeEnum.PARALLEL_SPLIT_GATEWAY.equals(node.getNodeType())
                && paired != null && NodeTypeEnum.PARALLEL_JOIN_GATEWAY.equals(paired.getNodeType());
        boolean joinToSplit = NodeTypeEnum.PARALLEL_JOIN_GATEWAY.equals(node.getNodeType())
                && paired != null && NodeTypeEnum.PARALLEL_SPLIT_GATEWAY.equals(paired.getNodeType());
        if ((!splitToJoin && !joinToSplit) || !node.getNodeCode().equals(paired.getPairedGatewayCode())) {
            throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                    "parallel gateway pair is invalid: " + node.getNodeCode());
        }
        return paired;
    }

    private ProcessEdgeDTO requireSingleOutgoing(ProcessNodeDTO node, DefinitionGraphIndex graph) {
        List<ProcessEdgeDTO> outgoing = graph.getOutgoingEdges(node.getNodeCode());
        if (outgoing.size() != 1) {
            throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                    "node must have exactly one outgoing edge: " + node.getNodeCode());
        }
        return outgoing.get(0);
    }

    private void completeInstance(ProcessInstanceEntity instance, RuntimeAdvanceResult result) {
        if (activeTaskRepository.countOpenByInstanceId(instance.getId()) != 0L
                || taskGroupRepository.countActiveByInstanceId(instance.getId()) != 0L) {
            throw state(RuntimeErrorCodes.INVALID_ACTION,
                    "instance cannot end while active tasks or task groups exist");
        }
        if (instanceRepository.updateCurrentNodeCodes(instance.getId(), RuntimeJsonCodec.toJson(new ArrayList<String>())) != 1
                || instanceRepository.complete(instance.getId(), LocalDateTime.now()) != 1) {
            throw state(RuntimeErrorCodes.INVALID_ACTION, "instance cannot be completed");
        }
        result.markInstanceCompleted();
    }

    private void refreshCurrentNodeCodes(String instanceId) {
        Set<String> nodeCodes = new LinkedHashSet<String>();
        for (ProcessActiveTaskEntity activeTask : activeTaskRepository.findOpenByInstanceId(instanceId)) {
            if (hasText(activeTask.getNodeCode())) {
                nodeCodes.add(activeTask.getNodeCode());
            }
        }
        if (instanceRepository.updateCurrentNodeCodes(instanceId,
                RuntimeJsonCodec.toJson(new ArrayList<String>(nodeCodes))) != 1) {
            throw state(RuntimeErrorCodes.INVALID_ACTION, "instance cannot refresh current nodes");
        }
    }

    private Map<String, String> readBranchStates(ProcessTaskGroupEntity group) {
        Map<String, Object> source = readObjectMap(group.getBranchStateJson(), "parallel branch state");
        Map<String, String> states = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (!(entry.getValue() instanceof String)) {
                throw state(RuntimeErrorCodes.PARALLEL_JOIN_CONFLICT,
                        "parallel branch state is malformed");
            }
            states.put(entry.getKey(), (String) entry.getValue());
        }
        return states;
    }

    private Map<String, Object> readObjectMap(String json, String fieldName) {
        try {
            return RuntimeJsonCodec.readObjectMap(json);
        } catch (IllegalArgumentException ex) {
            throw state(RuntimeErrorCodes.DEFINITION_INVALID, fieldName + " is malformed");
        }
    }

    private int maxAutomaticSteps(DefinitionGraphIndex graph) {
        return graph.getNodes().size() + graph.getEdges().size() + 1;
    }

    private void assertInput(ProcessInstanceEntity instance,
                             ProcessDefinitionDetailDTO definition,
                             String targetNodeCode,
                             String taskGroupId,
                             String branchKey) {
        if (instance == null || !hasText(instance.getId()) || !hasText(instance.getDefinitionId())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                    "running instance and definitionId are required");
        }
        if (definition == null || !instance.getDefinitionId().equals(definition.getId())) {
            throw new RuntimeStateException(RuntimeErrorCodes.DEFINITION_INVALID,
                    "runtime definition does not match instance definitionId");
        }
        if (!hasText(targetNodeCode)) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                    "targetNodeCode is required");
        }
        if (hasText(taskGroupId) != hasText(branchKey)) {
            throw new RuntimeValidationException(RuntimeErrorCodes.PARALLEL_JOIN_CONFLICT,
                    "taskGroupId and branchKey must be provided together");
        }
    }

    private static RuntimeStateException state(String errorCode, String message) {
        return new RuntimeStateException(errorCode, message);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    /** 单条自动路径的环路检测状态，分支拆分后各自复制。 */
    private static final class AdvancePathContext {
        private final int maximumSteps;
        private final Set<String> visitedNodeCodes;
        private int steps;

        private AdvancePathContext(int maximumSteps) {
            this(maximumSteps, new LinkedHashSet<String>(), 0);
        }

        private AdvancePathContext(int maximumSteps, Set<String> visitedNodeCodes, int steps) {
            this.maximumSteps = maximumSteps;
            this.visitedNodeCodes = visitedNodeCodes;
            this.steps = steps;
        }

        private void enterAutomaticNode(String nodeCode) {
            steps++;
            if (steps > maximumSteps || !visitedNodeCodes.add(nodeCode)) {
                throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                        "automatic route contains a cycle: " + nodeCode);
            }
        }

        private AdvancePathContext copyForBranch() {
            return new AdvancePathContext(maximumSteps, new LinkedHashSet<String>(visitedNodeCodes), steps);
        }
    }
}
