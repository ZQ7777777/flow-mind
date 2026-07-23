package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.core.task.HistoryTaskWriter;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.ProcessTaskGroupEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.TaskGroupRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 实例级管理动作的任务取消协调器。
 *
 * <p>终止、跳转和强制办结均通过本组件取消开放任务和活动任务组，避免三处实现不同的
 * CAS、归档和冲突处理顺序。调用方必须把整个调用放在运行时主事务中。</p>
 *
 * @author FlowMind
 * @since 2026-07-23
 */
@Component
public class InstanceTaskCancellationService {

    private final ActiveTaskRepository activeTaskRepository;
    private final TaskGroupRepository taskGroupRepository;
    private final HistoryTaskWriter historyTaskWriter;

    /** 创建实例级任务取消协调器。 */
    public InstanceTaskCancellationService(ActiveTaskRepository activeTaskRepository,
                                           TaskGroupRepository taskGroupRepository,
                                           HistoryTaskWriter historyTaskWriter) {
        this.activeTaskRepository = activeTaskRepository;
        this.taskGroupRepository = taskGroupRepository;
        this.historyTaskWriter = historyTaskWriter;
    }

    /**
     * 取消当前实例全部开放任务和活动任务组，并将被取消任务归档。
     *
     * @param instance          当前流程实例
     * @param operator          可信操作人
     * @param actionType        终止、跳转或强制办结动作
     * @param reason            操作说明
     * @param operationId       幂等操作号
     * @param variablesSnapshot 取消时变量快照
     * @return 本次取消产生的历史任务 DTO
     */
    public List<HistoryTaskDTO> cancelOpenWork(ProcessInstanceEntity instance,
                                                UserContext operator,
                                                ActionTypeEnum actionType,
                                                String reason,
                                                String operationId,
                                                Map<String, Object> variablesSnapshot) {
        List<HistoryTaskDTO> archivedTasks = new ArrayList<HistoryTaskDTO>();
        for (ProcessActiveTaskEntity task : activeTaskRepository.findOpenByInstanceId(instance.getId())) {
            Long version = task.getLockVersion();
            if (version == null || activeTaskRepository.cancel(task.getId(), version.longValue()) != 1) {
                throw new RuntimeStateException(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED,
                        "active task was modified while cancelling: " + task.getId());
            }
            ProcessHistoryTaskEntity history = historyTaskWriter.archiveCanceledTask(instance, task, operator,
                    actionType, reason, variablesSnapshot, operationId);
            archivedTasks.add(RuntimeModelMapper.toDto(history));
        }
        for (ProcessTaskGroupEntity group : taskGroupRepository.findActiveByInstanceId(instance.getId())) {
            Long version = group.getLockVersion();
            if (version == null || taskGroupRepository.cancel(group.getId(), version.longValue()) != 1) {
                throw new RuntimeStateException(RuntimeErrorCodes.TASK_GROUP_CONCURRENT_MODIFIED,
                        "task group was modified while cancelling: " + group.getId());
            }
        }
        return archivedTasks;
    }
}
