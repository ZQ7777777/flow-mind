package com.flowmind.platform.api.error;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 平台稳定错误码到错误类别的公共分类器。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
public final class PlatformErrorCategories {

    private static final Set<String> FORBIDDEN_CODES = new HashSet<String>(Arrays.asList(
            PlatformErrorCodes.TASK_PERMISSION_DENIED,
            PlatformErrorCodes.ADMIN_PERMISSION_DENIED,
            PlatformErrorCodes.ATTACHMENT_PERMISSION_DENIED,
            PlatformErrorCodes.WITHDRAW_PERMISSION_DENIED,
            PlatformErrorCodes.TASK_CLAIM_PERMISSION_DENIED));

    private static final Set<String> NOT_FOUND_CODES = new HashSet<String>(Arrays.asList(
            PlatformErrorCodes.DEFINITION_NOT_FOUND,
            PlatformErrorCodes.NODE_NOT_FOUND,
            PlatformErrorCodes.INSTANCE_NOT_FOUND,
            PlatformErrorCodes.TASK_NOT_FOUND,
            PlatformErrorCodes.TASK_GROUP_NOT_FOUND,
            PlatformErrorCodes.ATTACHMENT_NOT_FOUND,
            PlatformErrorCodes.ATTACHMENT_TEMPLATE_NOT_FOUND,
            PlatformErrorCodes.ALERT_NOT_FOUND,
            PlatformErrorCodes.TARGET_USER_NOT_FOUND));

    private static final Set<String> CONFLICT_CODES = new HashSet<String>(Arrays.asList(
            PlatformErrorCodes.OPERATION_ID_CONFLICT,
            PlatformErrorCodes.OPERATION_IN_PROGRESS,
            PlatformErrorCodes.DEFINITION_VERSION_CONFLICT,
            PlatformErrorCodes.DEFINITION_CONCURRENT_MODIFIED,
            PlatformErrorCodes.TASK_CONCURRENT_MODIFIED,
            PlatformErrorCodes.TASK_GROUP_CONCURRENT_MODIFIED,
            PlatformErrorCodes.CALLBACK_EVENT_CONFLICT,
            PlatformErrorCodes.PARALLEL_JOIN_CONFLICT,
            PlatformErrorCodes.TASK_ALREADY_CLAIMED,
            PlatformErrorCodes.TASK_NOT_CLAIMED,
            PlatformErrorCodes.ALERT_ALREADY_CLOSED));

    private PlatformErrorCategories() {
    }

    /**
     * 按稳定错误码解析类别，未知错误码使用调用方提供的默认类别。
     *
     * @param errorCode 错误码
     * @param fallback 默认类别
     * @return 错误类别
     */
    public static PlatformErrorCategory resolve(String errorCode, PlatformErrorCategory fallback) {
        if (FORBIDDEN_CODES.contains(errorCode)) {
            return PlatformErrorCategory.FORBIDDEN;
        }
        if (NOT_FOUND_CODES.contains(errorCode)) {
            return PlatformErrorCategory.NOT_FOUND;
        }
        if (CONFLICT_CODES.contains(errorCode)) {
            return PlatformErrorCategory.CONFLICT;
        }
        if (PlatformErrorCodes.ATTACHMENT_STORAGE_FAILED.equals(errorCode)) {
            return PlatformErrorCategory.DEPENDENCY_FAILURE;
        }
        return fallback == null ? PlatformErrorCategory.INTERNAL : fallback;
    }
}
