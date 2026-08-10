package com.flowmind.platform.api.error;

/**
 * 平台错误类别，供同进程宿主稳定映射传输层状态。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
public enum PlatformErrorCategory {
    INVALID_REQUEST,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    DEPENDENCY_FAILURE,
    TEMPORARILY_UNAVAILABLE,
    INTERNAL
}
