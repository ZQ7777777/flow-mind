package com.flowmind.business.workflow.dto;

import java.util.ArrayList;
import java.util.List;

public class WorkflowTaskActionResponse {
    private String operationId;
    private WorkflowInstanceResponse instance;
    private List<WorkflowHistoryTaskResponse> archivedTasks = new ArrayList<WorkflowHistoryTaskResponse>();
    private List<WorkflowTaskResponse> createdTasks = new ArrayList<WorkflowTaskResponse>();
    private List<WorkflowTaskResponse> updatedTasks = new ArrayList<WorkflowTaskResponse>();
    private boolean replayed;

    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public WorkflowInstanceResponse getInstance() { return instance; }
    public void setInstance(WorkflowInstanceResponse instance) { this.instance = instance; }
    public List<WorkflowHistoryTaskResponse> getArchivedTasks() { return archivedTasks; }
    public void setArchivedTasks(List<WorkflowHistoryTaskResponse> archivedTasks) { this.archivedTasks = archivedTasks; }
    public List<WorkflowTaskResponse> getCreatedTasks() { return createdTasks; }
    public void setCreatedTasks(List<WorkflowTaskResponse> createdTasks) { this.createdTasks = createdTasks; }
    public List<WorkflowTaskResponse> getUpdatedTasks() { return updatedTasks; }
    public void setUpdatedTasks(List<WorkflowTaskResponse> updatedTasks) { this.updatedTasks = updatedTasks; }
    public boolean isReplayed() { return replayed; }
    public void setReplayed(boolean replayed) { this.replayed = replayed; }
}
