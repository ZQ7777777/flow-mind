package com.flowmind.platform.core.runtime;

/**
 * M2 运行期组件使用的稳定错误码。
 */
public final class RuntimeErrorCodes {

    public static final String OPERATOR_REQUIRED = "FLOW_OPERATOR_REQUIRED";
    public static final String INSTANCE_NOT_FOUND = "FLOW_INSTANCE_NOT_FOUND";
    public static final String INSTANCE_STATUS_INVALID = "FLOW_INSTANCE_STATUS_INVALID";
    public static final String TASK_NOT_FOUND = "FLOW_TASK_NOT_FOUND";
    public static final String TASK_STATUS_INVALID = "FLOW_TASK_STATUS_INVALID";
    public static final String TASK_CONCURRENT_MODIFIED = "FLOW_TASK_CONCURRENT_MODIFIED";
    public static final String TASK_GROUP_NOT_FOUND = "FLOW_TASK_GROUP_NOT_FOUND";
    public static final String TASK_GROUP_STATUS_INVALID = "FLOW_TASK_GROUP_STATUS_INVALID";
    public static final String TASK_GROUP_CONCURRENT_MODIFIED = "FLOW_TASK_GROUP_CONCURRENT_MODIFIED";
    public static final String CALLBACK_EVENT_CONFLICT = "FLOW_CALLBACK_EVENT_CONFLICT";
    public static final String HISTORY_ARCHIVE_INVALID = "FLOW_HISTORY_ARCHIVE_INVALID";

    private RuntimeErrorCodes() {
    }
}
