package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.TaskGroupTypeEnum;
import com.flowmind.platform.core.validation.DefinitionGraphIndex;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.ProcessTaskGroupEntity;
import com.flowmind.platform.persistence.repository.TaskGroupRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 定义级会签审批的内部协调器。
 *
 * <p>当前任务的 CAS 和历史归档仍由统一运行时服务负责；本组件冻结会签组上下文、
 * 执行组计数 CAS，并只允许最后一名审批人沿流程出线推进。</p>
 *
 * @author FlowMind
 * @since 2026-07-28
 */
@Component
public class CountersignTaskCoordinator {

    private static final int MAX_GROUP_UPDATE_ATTEMPTS = 3;
    private static final String ADD_SIGN_PURPOSE = "ADD_SIGN";

    private final TaskGroupRepository taskGroupRepository;
    private final RuntimeNodeAdvancer nodeAdvancer;

    /** 创建会签审批协调器。 */
    public CountersignTaskCoordinator(TaskGroupRepository taskGroupRepository,
                                      RuntimeNodeAdvancer nodeAdvancer) {
        this.taskGroupRepository = taskGroupRepository;
        this.nodeAdvancer = nodeAdvancer;
    }

    /** 判断活动任务是否属于定义级会签组；M5 加签临时组明确排除。 */
    public boolean isCountersignTask(ProcessActiveTaskEntity task) {
        if (task == null || isBlank(task.getTaskGroupId())) {
            return false;
        }
        ProcessTaskGroupEntity group = taskGroupRepository.findById(task.getTaskGroupId());
        return group != null && TaskGroupTypeEnum.COUNTERSIGN.name().equals(group.getGroupType())
                && !isAddSignGroup(group);
    }

    /**
     * 在任务 CAS 前冻结组、唯一出线和后续审批人解析结果。
     */
    public CountersignAdvanceContext prepare(ProcessInstanceEntity instance,
                                             ProcessDefinitionDetailDTO definition,
                                             ProcessActiveTaskEntity task,
                                             ProcessNodeDTO node) {
        ProcessTaskGroupEntity group = taskGroupRepository.findById(task.getTaskGroupId());
        assertCountersignContext(instance, task, node, group);
        DefinitionGraphIndex graph = DefinitionGraphIndex.from(definition);
        java.util.List<ProcessEdgeDTO> outgoing = graph.getOutgoingEdges(node.getNodeCode());
        if (outgoing.size() != 1) {
            throw state(RuntimeErrorCodes.GATEWAY_CONFIG_INVALID,
                    "countersign user task requires exactly one outgoing edge: " + node.getNodeCode());
        }
        String targetNodeCode = outgoing.get(0).getTargetNodeCode();
        RuntimeAdvancePreparation preparation = nodeAdvancer.prepareAdvance(instance, definition, targetNodeCode,
                group.getParentGroupId(), group.getParentBranchKey());
        return new CountersignAdvanceContext(instance, definition, group, targetNodeCode, preparation);
    }

    /**
     * 增加会签完成计数；非最后一人不推进，最后一人使用外层父上下文唯一推进。
     */
    public RuntimeAdvanceResult completeAndAdvance(CountersignAdvanceContext context) {
        ProcessTaskGroupEntity group = context.group;
        for (int attempt = 0; attempt < MAX_GROUP_UPDATE_ATTEMPTS; attempt++) {
            assertActiveGroup(group);
            boolean completesGroup = group.getCompletedCount().intValue() + 1 == group.getTotalCount().intValue();
            if (taskGroupRepository.incrementCompletedCount(group.getId(),
                    group.getLockVersion().longValue()) == 1) {
                if (!completesGroup) {
                    return new RuntimeAdvanceResult();
                }
                return nodeAdvancer.advanceToNode(context.instance, context.definition, context.targetNodeCode,
                        group.getParentGroupId(), group.getParentBranchKey(), context.preparation);
            }
            group = taskGroupRepository.findById(group.getId());
            if (group == null || !"ACTIVE".equals(group.getGroupStatus())) {
                throw state(RuntimeErrorCodes.TASK_GROUP_CONCURRENT_MODIFIED,
                        "countersign task group reached a terminal state");
            }
        }
        throw state(RuntimeErrorCodes.TASK_GROUP_CONCURRENT_MODIFIED,
                "countersign task group was concurrently modified");
    }

    private void assertCountersignContext(ProcessInstanceEntity instance,
                                          ProcessActiveTaskEntity task,
                                          ProcessNodeDTO node,
                                          ProcessTaskGroupEntity group) {
        if (!MultiInstanceModeEnum.COUNTERSIGN.equals(node.getMultiInstanceMode())
                || group == null
                || !TaskGroupTypeEnum.COUNTERSIGN.name().equals(group.getGroupType())
                || isAddSignGroup(group)
                || !instance.getId().equals(group.getInstanceId())
                || !node.getNodeCode().equals(group.getNodeCode())
                || !group.getId().equals(task.getTaskGroupId())) {
            throw state(RuntimeErrorCodes.TASK_GROUP_STATUS_INVALID, "countersign task group context is invalid");
        }
        assertActiveGroup(group);
    }

    private void assertActiveGroup(ProcessTaskGroupEntity group) {
        if (!"ACTIVE".equals(group.getGroupStatus())
                || group.getCompletedCount() == null || group.getTotalCount() == null
                || group.getLockVersion() == null
                || group.getCompletedCount().intValue() < 0
                || group.getCompletedCount().intValue() >= group.getTotalCount().intValue()) {
            throw state(RuntimeErrorCodes.TASK_GROUP_CONCURRENT_MODIFIED,
                    "countersign task group is not active");
        }
    }

    private boolean isAddSignGroup(ProcessTaskGroupEntity group) {
        try {
            Map<String, Object> state = RuntimeJsonCodec.readObjectMap(group.getBranchStateJson());
            return ADD_SIGN_PURPOSE.equals(state.get("purpose"));
        } catch (IllegalArgumentException ex) {
            throw state(RuntimeErrorCodes.TASK_GROUP_STATUS_INVALID, "countersign task group state is malformed");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static RuntimeStateException state(String code, String message) {
        return new RuntimeStateException(code, message);
    }

    /** 会签任务 CAS 前冻结的后续推进上下文。 */
    public static final class CountersignAdvanceContext {
        private final ProcessInstanceEntity instance;
        private final ProcessDefinitionDetailDTO definition;
        private final ProcessTaskGroupEntity group;
        private final String targetNodeCode;
        private final RuntimeAdvancePreparation preparation;

        private CountersignAdvanceContext(ProcessInstanceEntity instance,
                                          ProcessDefinitionDetailDTO definition,
                                          ProcessTaskGroupEntity group,
                                          String targetNodeCode,
                                          RuntimeAdvancePreparation preparation) {
            this.instance = instance;
            this.definition = definition;
            this.group = group;
            this.targetNodeCode = targetNodeCode;
            this.preparation = preparation;
        }
    }
}
