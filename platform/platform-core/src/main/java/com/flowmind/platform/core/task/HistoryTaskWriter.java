package com.flowmind.platform.core.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.HandleTypeEnum;
import com.flowmind.platform.core.runtime.RuntimeErrorCodes;
import com.flowmind.platform.core.runtime.RuntimeTaskContext;
import com.flowmind.platform.core.runtime.RuntimeValidationException;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 历史任务写入组件。
 *
 * <p>这个类的职责是把已经被办理完成或取消的活动任务归档到
 * {@code process_history_task}，形成流程轨迹和审批意见的数据来源。
 * 审批意见写入 {@code comment_text}，流程变量快照写入
 * {@code variables_snapshot}。</p>
 *
 * <p>调用边界：B 线主流程必须先完成活动任务的 CAS 状态更新，例如把
 * {@code process_active_task} 从 {@code ACTIVE/CLAIMED} 更新为
 * {@code COMPLETED/CANCELED}。CAS 成功后，才能在同一个业务事务中调用
 * 本组件写历史任务。CAS 失败时不能调用本组件，否则会产生和主状态不一致
 * 的流程轨迹。</p>
 *
 * <p>本组件不负责流程流转决策，不创建下一任务，不更新实例状态，也不写
 * 审计日志或运行时附件。</p>
 */
@Service
public class HistoryTaskWriter {

    private final ProcessHistoryTaskRepository historyTaskRepository;
    private final ObjectMapper objectMapper;

    public HistoryTaskWriter(ProcessHistoryTaskRepository historyTaskRepository) {
        this.historyTaskRepository = historyTaskRepository;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    /**
     * 归档一个已成功办理完成的活动任务。
     *
     * <p>典型场景是申请节点提交、部门经理审批通过、财务确认通过等。
     * 外层业务层传入 {@link RuntimeTaskContext}，本方法会从上下文中提取
     * 实例、活动任务和操作人快照，再构造 {@link HistoryArchiveCommand}
     * 统一落库。</p>
     *
     * @param context           运行期任务上下文，来自 {@code RuntimeStateValidator}
     * @param actionType        本次归档动作，例如 SEND 或 APPROVE
     * @param comment           审批意见或办理说明，写入 comment_text
     * @param variablesSnapshot 办理完成时的流程变量快照，序列化到 variables_snapshot
     * @param operationId       本次操作幂等号，用于防止同一动作重复归档
     * @return 新写入或已存在的历史任务记录
     */
    public ProcessHistoryTaskEntity archiveCompletedTask(RuntimeTaskContext context,
                                                         ActionTypeEnum actionType,
                                                         String comment,
                                                         Map<String, Object> variablesSnapshot,
                                                         String operationId) {
        HistoryArchiveCommand command = new HistoryArchiveCommand();
        command.setInstance(context.getInstance());
        command.setTask(context.getTask());
        command.setOperator(context.getOperator());
        command.setActionType(actionType);
        command.setHandleType(resolveHandleType(context.getTask()));
        command.setComment(comment);
        command.setVariablesSnapshot(variablesSnapshot);
        command.setOperationId(operationId);
        command.setCompletedAt(LocalDateTime.now());
        return archive(command);
    }

    /**
     * 归档一个被取消的活动任务。
     *
     * <p>当前 M2 串行审批主链路主要使用 {@link #archiveCompletedTask}。
     * 这个方法为后续或签取消其他候选任务、流程终止取消待办等场景预留。
     * 由于只传入活动任务实体，这里只构造最小实例上下文，实例完整信息仍由
     * 外层业务事务保证。</p>
     *
     * @param task        被取消的活动任务
     * @param actionType  取消类动作，通常为 CANCEL 或 TERMINATE 派生动作
     * @param reason      取消原因，写入 comment_text
     * @param operationId 本次操作幂等号
     * @return 新写入或已存在的历史任务记录
     */
    public ProcessHistoryTaskEntity archiveCanceledTask(ProcessActiveTaskEntity task,
                                                        ActionTypeEnum actionType,
                                                        String reason,
                                                        String operationId) {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setId(task.getInstanceId());
        return archiveCanceledTask(instance, task, null, actionType, reason, null, operationId);
    }

    /**
     * 归档被实例级管理动作取消的任务，并保留实际操作人与变量快照。
     *
     * @param instance          所属流程实例
     * @param task              被取消的活动任务
     * @param operator          执行管理动作的可信用户
     * @param actionType        终止、跳转或强制办结等取消类动作
     * @param reason            处理说明
     * @param variablesSnapshot 取消时的流程变量快照
     * @param operationId       本次操作幂等号
     * @return 新写入或已存在的历史任务记录
     */
    public ProcessHistoryTaskEntity archiveCanceledTask(ProcessInstanceEntity instance,
                                                        ProcessActiveTaskEntity task,
                                                        UserContext operator,
                                                        ActionTypeEnum actionType,
                                                        String reason,
                                                        Map<String, Object> variablesSnapshot,
                                                        String operationId) {
        HistoryArchiveCommand command = new HistoryArchiveCommand();
        command.setInstance(instance);
        command.setTask(task);
        command.setOperator(operator);
        command.setActionType(actionType);
        command.setHandleType(resolveHandleType(task));
        command.setComment(reason);
        command.setVariablesSnapshot(variablesSnapshot);
        command.setOperationId(operationId);
        command.setCompletedAt(LocalDateTime.now());
        return archive(command);
    }

    /**
     * 批量归档历史任务。
     *
     * <p>用于一次业务动作影响多个活动任务的场景，例如后续或签成功后取消
     * 同组其它待办。列表为空或 null 时返回空列表。</p>
     *
     * @param commands 多条归档命令
     * @return 每条命令对应的新写入或已存在历史任务记录
     */
    public List<ProcessHistoryTaskEntity> archiveBatch(List<HistoryArchiveCommand> commands) {
        List<ProcessHistoryTaskEntity> archived = new ArrayList<ProcessHistoryTaskEntity>();
        if (commands == null) {
            return archived;
        }
        for (HistoryArchiveCommand command : commands) {
            archived.add(archive(command));
        }
        return archived;
    }

    /**
     * 执行单条历史任务归档。
     *
     * <p>幂等规则使用数据库唯一键对应的业务键：
     * {@code active_task_id + action_type + operation_id}。如果同一任务、同一动作、
     * 同一操作号已经归档过，本方法直接返回已有记录，不重复插入。</p>
     *
     * @param command 归档命令
     * @return 新写入或已存在的历史任务记录
     */
    public ProcessHistoryTaskEntity archive(HistoryArchiveCommand command) {
        validate(command);
        ProcessHistoryTaskEntity existing = historyTaskRepository.findByTaskActionOperation(
                command.getTask().getId(),
                command.getActionType().name(),
                command.getOperationId());
        if (existing != null) {
            return existing;
        }
        ProcessHistoryTaskEntity entity = toEntity(command);
        historyTaskRepository.insert(entity);
        return entity;
    }

    /**
     * 将业务归档命令转换为数据库实体。
     *
     * <p>字段来源以活动任务快照为主：节点、任务组、分支、开始时间都来自
     * {@code process_active_task}；实际办理人优先取当前操作人快照；审批意见
     * 来自命令中的 comment。</p>
     */
    private ProcessHistoryTaskEntity toEntity(HistoryArchiveCommand command) {
        ProcessActiveTaskEntity task = command.getTask();
        UserContext operator = command.getOperator();
        ProcessHistoryTaskEntity entity = new ProcessHistoryTaskEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setInstanceId(command.getInstance().getId());
        entity.setOperationId(command.getOperationId());
        entity.setActiveTaskId(task.getId());
        entity.setNodeCode(task.getNodeCode());
        entity.setTaskGroupId(task.getTaskGroupId());
        entity.setBranchKey(task.getBranchKey());
        entity.setAssigneeUserId(operator == null ? task.getAssigneeUserId() : operator.getUserId());
        entity.setAssigneeUserName(operator == null ? task.getAssigneeUserName() : operator.getUserName());
        entity.setDelegateFromUserId(task.getDelegateFromUserId());
        entity.setDelegateFromUserName(task.getDelegateFromUserName());
        entity.setHandleType(resolveCommandHandleType(command).name());
        entity.setActionType(command.getActionType().name());
        entity.setCommentText(command.getComment());
        entity.setVariablesSnapshot(writeVariables(command.getVariablesSnapshot()));
        entity.setStartedAt(task.getCreatedAt());
        entity.setCompletedAt(command.getCompletedAt() == null ? LocalDateTime.now() : command.getCompletedAt());
        entity.setExtraJson(command.getExtraJson());
        return entity;
    }

    private HandleTypeEnum resolveHandleType(ProcessActiveTaskEntity task) {
        return task != null && task.getDelegateFromUserId() != null
                && !task.getDelegateFromUserId().trim().isEmpty()
                ? HandleTypeEnum.DELEGATE : HandleTypeEnum.NORMAL;
    }

    private HandleTypeEnum resolveCommandHandleType(HistoryArchiveCommand command) {
        if (command.getHandleType() == null || HandleTypeEnum.NORMAL.equals(command.getHandleType())) {
            return resolveHandleType(command.getTask());
        }
        return command.getHandleType();
    }

    /**
     * 校验归档命令的最小必填字段。
     *
     * <p>这里只做历史写入所需字段校验，不校验任务状态和实例状态。任务状态、
     * 实例状态、版本号应在调用本组件前由 {@code RuntimeStateValidator}
     * 和活动任务 CAS 更新共同保证。</p>
     */
    private void validate(HistoryArchiveCommand command) {
        if (command == null || command.getTask() == null || command.getInstance() == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.HISTORY_ARCHIVE_INVALID,
                    "history archive command is incomplete");
        }
        if (command.getActionType() == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.HISTORY_ARCHIVE_INVALID,
                    "history actionType is required");
        }
        if (command.getOperationId() == null || command.getOperationId().trim().isEmpty()) {
            throw new RuntimeValidationException(RuntimeErrorCodes.HISTORY_ARCHIVE_INVALID,
                    "history operationId is required");
        }
    }

    /**
     * 序列化流程变量快照。
     *
     * <p>null 表示本次归档没有提供变量快照；非 null 时写成 JSON 对象字符串，
     * 存入 {@code process_history_task.variables_snapshot}。</p>
     */
    private String writeVariables(Map<String, Object> variablesSnapshot) {
        if (variablesSnapshot == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(variablesSnapshot);
        } catch (JsonProcessingException ex) {
            throw new RuntimeValidationException(RuntimeErrorCodes.HISTORY_ARCHIVE_INVALID,
                    "variables snapshot json write failed", ex);
        }
    }
}
