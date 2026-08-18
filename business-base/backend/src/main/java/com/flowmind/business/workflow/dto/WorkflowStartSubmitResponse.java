package com.flowmind.business.workflow.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用启动并提交成功响应。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@Data
public class WorkflowStartSubmitResponse {
    /** 新流程实例 ID。 */
    private String instanceId;
    /** 实例绑定的流程定义 ID。 */
    private String definitionId;
    /** 流程编码。 */
    private String processCode;
    /** 流程定义版本。 */
    private Integer definitionVersion;
    /** 实例状态。 */
    private String instanceStatus;
    /** 当前活动节点编码。 */
    private List<String> currentNodeCodes = new ArrayList<String>();
    /** 本次推进创建的任务。 */
    private List<CreatedTaskView> createdTasks = new ArrayList<CreatedTaskView>();

    /** 新建任务摘要。 */
    @Data
    public static class CreatedTaskView {
        /** 任务 ID。 */
        private String taskId;
        /** 节点编码。 */
        private String nodeCode;
        /** 节点名称。 */
        private String taskName;
    }
}
