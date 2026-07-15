package com.flowmind.platform.api.entity.dto;

import java.util.List;

/**
 * 流程实例详情。
 */
public class ProcessInstanceDetailDTO extends ProcessInstanceDTO {

    /** 当前活动任务列表。 */
    private List<TaskDTO> activeTasks;
    /** 历史任务列表。 */
    private List<HistoryTaskDTO> historyTasks;
    /** 审批意见列表。 */
    private List<ProcessCommentDTO> comments;

    public ProcessInstanceDetailDTO() {
    }

    public List<TaskDTO> getActiveTasks() {
        return activeTasks;
    }

    public void setActiveTasks(List<TaskDTO> activeTasks) {
        this.activeTasks = activeTasks;
    }

    public List<HistoryTaskDTO> getHistoryTasks() {
        return historyTasks;
    }

    public void setHistoryTasks(List<HistoryTaskDTO> historyTasks) {
        this.historyTasks = historyTasks;
    }

    public List<ProcessCommentDTO> getComments() {
        return comments;
    }

    public void setComments(List<ProcessCommentDTO> comments) {
        this.comments = comments;
    }
}
