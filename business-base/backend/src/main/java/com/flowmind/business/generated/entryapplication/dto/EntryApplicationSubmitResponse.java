package com.flowmind.business.generated.entryapplication.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 入金申请提交结果：流程实例 ID、实例状态以及创建出的下一步任务摘要。
 */
public class EntryApplicationSubmitResponse {

    private String instanceId;

    private String instanceStatus;

    private List<TaskSummary> tasks = Collections.emptyList();

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getInstanceStatus() {
        return instanceStatus;
    }

    public void setInstanceStatus(String instanceStatus) {
        this.instanceStatus = instanceStatus;
    }

    public List<TaskSummary> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskSummary> tasks) {
        this.tasks = tasks != null ? tasks : Collections.emptyList();
    }

    /**
     * 创建出的下一步任务摘要，由平台 {@code TaskDTO} 逐项映射而来。
     */
    public static class TaskSummary {

        private String taskId;

        private String nodeCode;

        private String taskName;

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getNodeCode() {
            return nodeCode;
        }

        public void setNodeCode(String nodeCode) {
            this.nodeCode = nodeCode;
        }

        public String getTaskName() {
            return taskName;
        }

        public void setTaskName(String taskName) {
            this.taskName = taskName;
        }
    }
}
