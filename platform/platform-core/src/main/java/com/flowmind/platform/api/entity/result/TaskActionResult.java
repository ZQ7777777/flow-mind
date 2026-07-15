package com.flowmind.platform.api.entity.result;

import com.flowmind.platform.api.entity.dto.HistoryTaskDTO;
import com.flowmind.platform.api.entity.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.entity.dto.TaskDTO;

import java.util.List;

public class TaskActionResult {

    /** 本次任务动作的幂等号。 */
    private String operationId;
    /** 动作执行后对应的流程实例快照。 */
    private ProcessInstanceDTO instance;
    /** 本次动作归档或取消的历史任务记录。 */
    private List<HistoryTaskDTO> archivedTasks;
    /** 本次动作推进后新建的活动任务；并行时可包含多个任务。 */
    private List<TaskDTO> createdTasks;
    /** 是否由相同幂等号的重复请求返回首次执行结果。 */
    private boolean replayed;

    public TaskActionResult() {
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public ProcessInstanceDTO getInstance() {
        return instance;
    }

    public void setInstance(ProcessInstanceDTO instance) {
        this.instance = instance;
    }

    public List<HistoryTaskDTO> getArchivedTasks() {
        return archivedTasks;
    }

    public void setArchivedTasks(List<HistoryTaskDTO> archivedTasks) {
        this.archivedTasks = archivedTasks;
    }

    public List<TaskDTO> getCreatedTasks() {
        return createdTasks;
    }

    public void setCreatedTasks(List<TaskDTO> createdTasks) {
        this.createdTasks = createdTasks;
    }

    public boolean isReplayed() {
        return replayed;
    }

    public void setReplayed(boolean replayed) {
        this.replayed = replayed;
    }
}
