package com.flowmind.platform.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务动作执行结果，返回任务归档、取消、新建任务和实例状态变化。
 *
 * @author Yuxin Xu
 * @since 2026-07-14
 */
@Data
public class TaskActionResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 被操作任务所属流程实例 ID。
     */
    private String instanceId;
    /**
     * 本次执行的动作类型。
     */
    private String actionType;
    /**
     * 本次动作完成并归档的任务 ID 列表。
     */
    private List<String> completedTaskIds = new ArrayList<String>();
    /**
     * 本次动作取消的任务 ID 列表。
     */
    private List<String> canceledTaskIds = new ArrayList<String>();
    /**
     * 本次动作推进后新创建的活动任务列表。
     */
    private List<TaskDTO> createdTasks = new ArrayList<TaskDTO>();
    /**
     * 动作完成后的流程实例状态。
     */
    private String instanceStatus;

}
