package com.flowmind.platform.api.dto;

import java.util.List;

public class TaskActionResult {

    private String operationId;
    private ProcessInstanceDTO instance;
    private List<HistoryTaskDTO> archivedTasks;
    private List<TaskDTO> createdTasks;
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
