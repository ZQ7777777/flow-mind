package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.ProcessNoticeDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次统一节点推进产生的运行时结果。
 *
 * <p>仅记录本次推进新建的活动任务与实例是否办结；任务归档和幂等结果由调用方的动作编排负责。</p>
 *
 * @author FlowMind
 * @since 2026-07-22
 */
public final class RuntimeAdvanceResult {

    /** 本次推进实际新建的活动任务。 */
    private final List<TaskDTO> createdTasks = new ArrayList<TaskDTO>();
    /** 本次推进产生的自动知会消息。 */
    private final List<ProcessNoticeDTO> createdNotices = new ArrayList<ProcessNoticeDTO>();
    /** 本次推进是否将实例办结。 */
    private boolean instanceCompleted;

    /** 返回不可变的新建任务视图。 */
    public List<TaskDTO> getCreatedTasks() {
        return Collections.unmodifiableList(createdTasks);
    }

    /** 返回实例是否在本次推进中办结。 */
    public boolean isInstanceCompleted() {
        return instanceCompleted;
    }

    /** 返回不可变的自动知会消息视图。 */
    public List<ProcessNoticeDTO> getCreatedNotices() {
        return Collections.unmodifiableList(createdNotices);
    }

    /** 供推进器收集一条新建活动任务。 */
    void addCreatedTask(TaskDTO task) {
        createdTasks.add(task);
    }

    /** 供推进器收集一条自动知会消息。 */
    void addCreatedNotice(ProcessNoticeDTO notice) {
        createdNotices.add(notice);
    }

    /** 供推进器标识实例已办结。 */
    void markInstanceCompleted() {
        instanceCompleted = true;
    }
}
