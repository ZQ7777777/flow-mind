package com.flowmind.business.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.business.platform.PlatformFacade;
import com.flowmind.platform.api.dto.DirectSendContextDTO;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.TaskStatusEnum;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class WorkflowAllowedActionResolver {
    private final ObjectMapper objectMapper;
    private final PlatformFacade platformFacade;
    private final WorkflowWithdrawContextResolver withdrawContextResolver = new WorkflowWithdrawContextResolver();

    public WorkflowAllowedActionResolver(ObjectMapper objectMapper, PlatformFacade platformFacade) {
        this.objectMapper = objectMapper;
        this.platformFacade = platformFacade;
    }

    /**
     * 根据任务状态、当前用户关系、节点规则和历史轨迹保守计算前端可展示动作。
     *
     * <p>该列表只用于页面按钮展示；真正能否执行仍以平台动作接口的校验结果为准。</p>
     */
    public List<String> resolve(TaskDTO task, ProcessDefinitionDetailDTO definition,
                                List<TaskDTO> activeTasks, List<HistoryTaskDTO> histories, String userId) {
        return resolve(task, definition, null, activeTasks, histories, userId);
    }

    public List<String> resolve(TaskDTO task, ProcessDefinitionDetailDTO definition,
                                ProcessInstanceDetailDTO instance, List<TaskDTO> activeTasks,
                                List<HistoryTaskDTO> histories, String userId) {
        if (task == null) return Collections.emptyList();
        List<String> actions = new ArrayList<String>();
        List<String> candidates = safe(task.getCandidateUserIds());
        boolean assigned = hasText(task.getAssigneeUserId());
        boolean assignee = userId != null && userId.equals(task.getAssigneeUserId());
        boolean candidate = candidates.contains(userId);
        boolean canHandle = assigned ? assignee : candidate;
        if (requiresClaim(task, userId)) actions.add("CLAIM");
        if (TaskStatusEnum.CLAIMED.equals(task.getTaskStatus()) && assignee) actions.add("UNCLAIM");
        ProcessNodeDTO node = findNode(definition, task.getNodeCode());
        if (canHandle) {
            actions.add(node != null && ApproverRuleTypeEnum.STARTER.equals(node.getApproverRuleType()) ? "SUBMIT" : "APPROVE");
            actions.add("TRANSFER"); actions.add("DELEGATE"); actions.add("ADD_SIGN");
            if (node == null || !ApproverRuleTypeEnum.STARTER.equals(node.getApproverRuleType())) actions.add("RETURN");
            if (rejectEnabled(node)) actions.add("REJECT");
            try {
                DirectSendContextDTO context = platformFacade.directSendContext(task.getTaskId());
                if (context != null && context.isAllowed()) actions.add("DIRECT_SEND");
            } catch (RuntimeException ignored) {
                // Capability discovery is advisory; the action endpoint remains authoritative.
            }
        }
        WorkflowWithdrawContextResolver.Resolution withdraw = withdrawContextResolver.resolve(instance, definition, userId);
        if (withdraw != null && withdraw.getActiveTask() != null
                && task.getTaskId().equals(withdraw.getActiveTask().getTaskId())) actions.add("WITHDRAW");
        return actions;
    }

    public List<String> resolveDisabled(TaskDTO task, List<String> allowedActions, String userId) {
        if (!requiresClaim(task, userId)) return Collections.emptyList();
        List<String> disabled = new ArrayList<String>();
        for (String action : safe(allowedActions)) {
            if (!"CLAIM".equals(action)) disabled.add(action);
        }
        return disabled;
    }

    /**
     * 从节点监听配置中读取驳回规则，只有显式启用且配置目标节点时才展示驳回。
     */
    private boolean rejectEnabled(ProcessNodeDTO node) {
        if (node == null || !hasText(node.getListenerConfig())) return false;
        try {
            JsonNode reject = objectMapper.readTree(node.getListenerConfig()).path("taskActionRules").path("reject");
            return reject.path("enabled").asBoolean(false) && reject.path("targetNodeCodes").isArray()
                    && reject.path("targetNodeCodes").size() > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 在流程定义中按节点编码定位当前任务节点。
     */
    private ProcessNodeDTO findNode(ProcessDefinitionDetailDTO definition, String nodeCode) {
        for (ProcessNodeDTO node : safe(definition == null ? null : definition.getNodes())) {
            if (nodeCode.equals(node.getNodeCode())) return node;
        }
        return null;
    }

    private boolean requiresClaim(TaskDTO task, String userId) {
        if (task == null || !TaskStatusEnum.ACTIVE.equals(task.getTaskStatus())
                || hasText(task.getAssigneeUserId())) {
            return false;
        }
        List<String> candidates = safe(task.getCandidateUserIds());
        return candidates.contains(userId) && hasMultipleCandidates(candidates);
    }

    private boolean hasMultipleCandidates(List<String> candidates) {
        String first = null;
        for (String candidate : candidates) {
            if (!hasText(candidate)) continue;
            if (first == null) first = candidate;
            else if (!first.equals(candidate)) return true;
        }
        return false;
    }

    /**
     * 判断字符串是否包含有效文本。
     */
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }

    /**
     * 将空列表归一化，简化动作推导中的遍历逻辑。
     */
    private <T> List<T> safe(List<T> values) { return values == null ? Collections.<T>emptyList() : values; }
}
