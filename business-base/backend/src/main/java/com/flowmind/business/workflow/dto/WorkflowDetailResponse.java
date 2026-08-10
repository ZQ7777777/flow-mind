package com.flowmind.business.workflow.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WorkflowDetailResponse {
    private WorkflowInstanceResponse instance;
    private DefinitionView definition;
    private List<FormFieldView> formFields = new ArrayList<FormFieldView>();
    private List<NodeView> nodes = new ArrayList<NodeView>();
    private List<EdgeView> edges = new ArrayList<EdgeView>();
    private WorkflowTaskResponse currentTask;
    private List<WorkflowTaskResponse> activeTasks = new ArrayList<WorkflowTaskResponse>();
    private List<WorkflowHistoryTaskResponse> historyTasks = new ArrayList<WorkflowHistoryTaskResponse>();
    private List<CommentView> comments = new ArrayList<CommentView>();
    private List<AttachmentView> attachments = new ArrayList<AttachmentView>();
    private List<String> allowedActions = new ArrayList<String>();

    public WorkflowInstanceResponse getInstance() { return instance; }
    public void setInstance(WorkflowInstanceResponse instance) { this.instance = instance; }
    public DefinitionView getDefinition() { return definition; }
    public void setDefinition(DefinitionView definition) { this.definition = definition; }
    public List<FormFieldView> getFormFields() { return formFields; }
    public void setFormFields(List<FormFieldView> formFields) { this.formFields = formFields; }
    public List<NodeView> getNodes() { return nodes; }
    public void setNodes(List<NodeView> nodes) { this.nodes = nodes; }
    public List<EdgeView> getEdges() { return edges; }
    public void setEdges(List<EdgeView> edges) { this.edges = edges; }
    public WorkflowTaskResponse getCurrentTask() { return currentTask; }
    public void setCurrentTask(WorkflowTaskResponse currentTask) { this.currentTask = currentTask; }
    public List<WorkflowTaskResponse> getActiveTasks() { return activeTasks; }
    public void setActiveTasks(List<WorkflowTaskResponse> activeTasks) { this.activeTasks = activeTasks; }
    public List<WorkflowHistoryTaskResponse> getHistoryTasks() { return historyTasks; }
    public void setHistoryTasks(List<WorkflowHistoryTaskResponse> historyTasks) { this.historyTasks = historyTasks; }
    public List<CommentView> getComments() { return comments; }
    public void setComments(List<CommentView> comments) { this.comments = comments; }
    public List<AttachmentView> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentView> attachments) { this.attachments = attachments; }
    public List<String> getAllowedActions() { return allowedActions; }
    public void setAllowedActions(List<String> allowedActions) { this.allowedActions = allowedActions; }

    public static class DefinitionView {
        private String processCode;
        private String processName;
        private Integer version;
        public String getProcessCode() { return processCode; }
        public void setProcessCode(String processCode) { this.processCode = processCode; }
        public String getProcessName() { return processName; }
        public void setProcessName(String processName) { this.processName = processName; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
    }

    public static class FormFieldView {
        private String fieldCode;
        private String fieldName;
        private String fieldType;
        private String controlType;
        private Boolean required;
        private String validationRule;
        private Integer sortOrder;
        public String getFieldCode() { return fieldCode; }
        public void setFieldCode(String fieldCode) { this.fieldCode = fieldCode; }
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }
        public String getControlType() { return controlType; }
        public void setControlType(String controlType) { this.controlType = controlType; }
        public Boolean getRequired() { return required; }
        public void setRequired(Boolean required) { this.required = required; }
        public String getValidationRule() { return validationRule; }
        public void setValidationRule(String validationRule) { this.validationRule = validationRule; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    public static class NodeView {
        private String nodeCode;
        private String nodeName;
        private String nodeType;
        private Double positionX;
        private Double positionY;
        private Integer sortOrder;
        public String getNodeCode() { return nodeCode; }
        public void setNodeCode(String nodeCode) { this.nodeCode = nodeCode; }
        public String getNodeName() { return nodeName; }
        public void setNodeName(String nodeName) { this.nodeName = nodeName; }
        public String getNodeType() { return nodeType; }
        public void setNodeType(String nodeType) { this.nodeType = nodeType; }
        public Double getPositionX() { return positionX; }
        public void setPositionX(Double positionX) { this.positionX = positionX; }
        public Double getPositionY() { return positionY; }
        public void setPositionY(Double positionY) { this.positionY = positionY; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    public static class EdgeView {
        private String edgeCode;
        private String sourceNodeCode;
        private String targetNodeCode;
        private Boolean defaultEdge;
        private Integer sortOrder;
        public String getEdgeCode() { return edgeCode; }
        public void setEdgeCode(String edgeCode) { this.edgeCode = edgeCode; }
        public String getSourceNodeCode() { return sourceNodeCode; }
        public void setSourceNodeCode(String sourceNodeCode) { this.sourceNodeCode = sourceNodeCode; }
        public String getTargetNodeCode() { return targetNodeCode; }
        public void setTargetNodeCode(String targetNodeCode) { this.targetNodeCode = targetNodeCode; }
        public Boolean getDefaultEdge() { return defaultEdge; }
        public void setDefaultEdge(Boolean defaultEdge) { this.defaultEdge = defaultEdge; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    public static class CommentView {
        private String commentId;
        private String taskId;
        private String nodeCode;
        private String operatorUserId;
        private String operatorUserName;
        private String comment;
        private LocalDateTime createdAt;
        public String getCommentId() { return commentId; }
        public void setCommentId(String commentId) { this.commentId = commentId; }
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public String getNodeCode() { return nodeCode; }
        public void setNodeCode(String nodeCode) { this.nodeCode = nodeCode; }
        public String getOperatorUserId() { return operatorUserId; }
        public void setOperatorUserId(String operatorUserId) { this.operatorUserId = operatorUserId; }
        public String getOperatorUserName() { return operatorUserName; }
        public void setOperatorUserName(String operatorUserName) { this.operatorUserName = operatorUserName; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class AttachmentView {
        private String attachmentId;
        private String instanceId;
        private String taskId;
        private String ownerType;
        private String attachmentCode;
        private String fieldCode;
        private String fileName;
        private String contentType;
        private Long sizeBytes;
        private String uploadedBy;
        private LocalDateTime uploadedAt;
        public String getAttachmentId() { return attachmentId; }
        public void setAttachmentId(String attachmentId) { this.attachmentId = attachmentId; }
        public String getInstanceId() { return instanceId; }
        public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public String getOwnerType() { return ownerType; }
        public void setOwnerType(String ownerType) { this.ownerType = ownerType; }
        public String getAttachmentCode() { return attachmentCode; }
        public void setAttachmentCode(String attachmentCode) { this.attachmentCode = attachmentCode; }
        public String getFieldCode() { return fieldCode; }
        public void setFieldCode(String fieldCode) { this.fieldCode = fieldCode; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public Long getSizeBytes() { return sizeBytes; }
        public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
        public String getUploadedBy() { return uploadedBy; }
        public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
        public LocalDateTime getUploadedAt() { return uploadedAt; }
        public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    }
}
