package com.flowmind.platform.core.task;

import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.HandleTypeEnum;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 历史任务归档命令，承载一次活动任务归档所需的快照数据。
 */
public class HistoryArchiveCommand {

    private ProcessInstanceEntity instance;
    private ProcessActiveTaskEntity task;
    private UserContext operator;
    private ActionTypeEnum actionType;
    private HandleTypeEnum handleType = HandleTypeEnum.NORMAL;
    private String operationId;
    private String comment;
    private Map<String, Object> variablesSnapshot;
    private LocalDateTime completedAt;
    private String extraJson;

    public ProcessInstanceEntity getInstance() {
        return instance;
    }

    public void setInstance(ProcessInstanceEntity instance) {
        this.instance = instance;
    }

    public ProcessActiveTaskEntity getTask() {
        return task;
    }

    public void setTask(ProcessActiveTaskEntity task) {
        this.task = task;
    }

    public UserContext getOperator() {
        return operator;
    }

    public void setOperator(UserContext operator) {
        this.operator = operator;
    }

    public ActionTypeEnum getActionType() {
        return actionType;
    }

    public void setActionType(ActionTypeEnum actionType) {
        this.actionType = actionType;
    }

    public HandleTypeEnum getHandleType() {
        return handleType;
    }

    public void setHandleType(HandleTypeEnum handleType) {
        this.handleType = handleType;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Map<String, Object> getVariablesSnapshot() {
        return variablesSnapshot;
    }

    public void setVariablesSnapshot(Map<String, Object> variablesSnapshot) {
        this.variablesSnapshot = variablesSnapshot;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getExtraJson() {
        return extraJson;
    }

    public void setExtraJson(String extraJson) {
        this.extraJson = extraJson;
    }
}
