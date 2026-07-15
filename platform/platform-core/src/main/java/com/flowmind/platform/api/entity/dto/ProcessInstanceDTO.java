package com.flowmind.platform.api.entity.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ProcessInstanceDTO {

    /** 流程实例 ID。 */
    private String instanceId;
    /** 实例所基于的流程定义 ID。 */
    private String definitionId;
    /** 实例启动时固化绑定的附件配置 ID。 */
    private String attachmentConfigId;
    /** 流程定义的业务编码。 */
    private String processCode;
    /** 实例启动时固化的流程名称快照。 */
    private String processName;
    /** 实例启动时固化的流程定义版本。 */
    private Integer version;
    /** 展示给流程参与者的实例标题。 */
    private String instanceTitle;
    /** 与外部业务系统关联的业务键。 */
    private String businessKey;
    /** 流程发起人的用户 ID。 */
    private String starterUserId;
    /** 流程发起人的名称快照。 */
    private String starterUserName;
    /** 发起流程时所属部门的 ID。 */
    private String starterDeptId;
    /** 当前等待办理的节点编码；并行流程可同时包含多个节点。 */
    private List<String> currentNodeCodes;
    /** 当前流程变量集合。 */
    private Map<String, Object> variables;
    /** 流程实例启动时间。 */
    private LocalDateTime startedAt;
    /** 流程实例结束或办结时间；未结束时可为空。 */
    private LocalDateTime endedAt;
    /** 本次启动或推进操作新建的活动任务；并行时可包含多个任务。 */
    private List<TaskDTO> createdTasks;

    public ProcessInstanceDTO() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public String getAttachmentConfigId() {
        return attachmentConfigId;
    }

    public void setAttachmentConfigId(String attachmentConfigId) {
        this.attachmentConfigId = attachmentConfigId;
    }

    public String getProcessCode() {
        return processCode;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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

    public String getStarterUserName() {
        return starterUserName;
    }

    public void setStarterUserName(String starterUserName) {
        this.starterUserName = starterUserName;
    }

    public String getStarterDeptId() {
        return starterDeptId;
    }

    public void setStarterDeptId(String starterDeptId) {
        this.starterDeptId = starterDeptId;
    }

    public List<String> getCurrentNodeCodes() {
        return currentNodeCodes;
    }

    public void setCurrentNodeCodes(List<String> currentNodeCodes) {
        this.currentNodeCodes = currentNodeCodes;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public List<TaskDTO> getCreatedTasks() {
        return createdTasks;
    }

    public void setCreatedTasks(List<TaskDTO> createdTasks) {
        this.createdTasks = createdTasks;
    }
}
