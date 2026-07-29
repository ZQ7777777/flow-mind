package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.InstanceStatusEnum;

import java.time.LocalDateTime;

/** Admin-side process instance query conditions. */
public class AdminInstanceQuery extends PageQuery {

    private String processCode;
    private InstanceStatusEnum instanceStatus;
    private String instanceTitle;
    private String businessKey;
    private String starterUserId;
    private String currentNodeCode;
    private LocalDateTime startedFrom;
    private LocalDateTime startedTo;

    public AdminInstanceQuery() {
    }

    public String getProcessCode() {
        return processCode;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    public InstanceStatusEnum getInstanceStatus() {
        return instanceStatus;
    }

    public void setInstanceStatus(InstanceStatusEnum instanceStatus) {
        this.instanceStatus = instanceStatus;
    }

    public String getInstanceTitle() {
        return instanceTitle;
    }

    public void setInstanceTitle(String instanceTitle) {
        this.instanceTitle = instanceTitle;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getStarterUserId() {
        return starterUserId;
    }

    public void setStarterUserId(String starterUserId) {
        this.starterUserId = starterUserId;
    }

    public String getCurrentNodeCode() {
        return currentNodeCode;
    }

    public void setCurrentNodeCode(String currentNodeCode) {
        this.currentNodeCode = currentNodeCode;
    }

    public LocalDateTime getStartedFrom() {
        return startedFrom;
    }

    public void setStartedFrom(LocalDateTime startedFrom) {
        this.startedFrom = startedFrom;
    }

    public LocalDateTime getStartedTo() {
        return startedTo;
    }

    public void setStartedTo(LocalDateTime startedTo) {
        this.startedTo = startedTo;
    }
}
